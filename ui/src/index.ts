import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiBarChartBoxLine from '~icons/ri/bar-chart-box-line'
import StatsWidget from './components/StatsWidget.vue'

const VIEW_PERMISSION = 'plugin:tinytraffic:stats:view'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/tinytraffic',
        name: 'Tinytraffic',
        component: () => import(/* webpackChunkName: "StatsView" */ './views/StatsView.vue'),
        meta: {
          title: '访问统计',
          searchable: true,
          permissions: [VIEW_PERMISSION],
          menu: {
            name: '访问统计',
            group: 'tool',
            icon: markRaw(RiBarChartBoxLine),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'console:dashboard:widgets:create': () => [
      {
        id: 'tinytraffic-stats',
        component: markRaw(StatsWidget),
        group: 'tinytraffic',
        defaultSize: { w: 3, h: 6, minW: 2, minH: 5 },
        permissions: [VIEW_PERMISSION],
      },
    ],
  },
})
