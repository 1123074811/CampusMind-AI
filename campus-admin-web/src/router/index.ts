import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

/** Placeholder — real view is resolved by App.vue via route.meta.nav */
const Blank = { template: '<span />' };

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/review' },
  { path: '/review', name: 'review', component: Blank, meta: { nav: 'review' } },
  { path: '/review/:id', name: 'review-detail', component: Blank, meta: { nav: 'review' } },
  { path: '/sources', name: 'sources', component: Blank, meta: { nav: 'sources' } },
  { path: '/tasks', name: 'tasks', component: Blank, meta: { nav: 'tasks' } },
  { path: '/logs', name: 'logs', component: Blank, meta: { nav: 'logs' } },
  { path: '/agent', name: 'agent', component: Blank, meta: { nav: 'agent', adminOnly: true } },
  { path: '/users', name: 'users', component: Blank, meta: { nav: 'users', adminOnly: true } },
  { path: '/database', name: 'database', component: Blank, meta: { nav: 'database', adminOnly: true } },
  { path: '/notifications', name: 'notifications', component: Blank, meta: { nav: 'notifications' } },
  { path: '/operations', name: 'operations', component: Blank, meta: { nav: 'operations' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
