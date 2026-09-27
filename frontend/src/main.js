// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信：zhuatech / zhuatech2
import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";
const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: "/:page?", component: { template: "<span></span>" } }],
});
createApp(App).use(router).mount("#app");
