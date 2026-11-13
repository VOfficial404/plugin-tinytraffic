package io.github.vofficial404.tinytraffic.tracker;

import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.dialect.TemplateHeadProcessor;

/** Adds the tinytraffic tracker script to the {@code <head>} of every theme page. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackerHeadProcessor implements TemplateHeadProcessor {

    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<Void> process(ITemplateContext context, IModel model, IElementModelStructureHandler structureHandler) {
        return Mono.zip(BasicSetting.fetch(settingFetcher), TrackerSetting.fetch(settingFetcher))
                .filter(settings -> !(settings.getT2().isExcludeLoggedIn() && isLoggedIn(context)))
                .flatMap(settings -> Mono.justOrEmpty(TrackerScript.render(settings.getT1(), settings.getT2())))
                .doOnNext(script -> model.add(context.getModelFactory().createText(script)))
                // The page must still render when the settings cannot be read.
                .onErrorResume(e -> {
                    log.warn("Failed to add the tinytraffic tracker script", e);
                    return Mono.empty();
                })
                .then();
    }

    /** Reads the signed-in user the way themes do, through the {@code #authentication} expression object. */
    private static boolean isLoggedIn(ITemplateContext context) {
        try {
            var object = context.getExpressionObjects().getObject("authentication");
            return object instanceof Authentication authentication
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && !AnonymousUserConst.isAnonymousUser(authentication.getName());
        } catch (RuntimeException e) {
            log.debug("Could not tell whether the visitor is signed in", e);
            return false;
        }
    }
}
