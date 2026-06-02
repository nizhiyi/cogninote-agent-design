# cogniNote-agent-front

CogniNote Agent 的 Vue 3 前端。第一阶段只实现基础应用壳和后端状态联调。

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm ci
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

Vite 会把 `/api` 代理到本地 Spring Boot 后端：

```text
http://127.0.0.1:18080
```

### Compile and Minify for Production

```sh
npm run build
```

生产构建产物会输出到 `dist/`，整包构建时由根目录 Maven `with-frontend` profile 复制进 Spring Boot 静态资源目录。
