<script setup lang="ts">
import { computed, ref } from 'vue';
import { createDemoSession, login } from '../api/auth';
import type { AdminSession } from '../adminTypes';

const emit = defineEmits<{
  authenticated: [session: AdminSession];
}>();

const username = ref('admin_seed');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

const canSubmit = computed(() => username.value.trim().length > 0 && password.value.length > 0 && !loading.value);

async function submitLogin() {
  if (!canSubmit.value) {
    return;
  }

  loading.value = true;
  errorMessage.value = '';
  try {
    const session = await login(username.value.trim(), password.value);
    emit('authenticated', session);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

function enterDemo() {
  emit('authenticated', createDemoSession());
}
</script>

<template>
  <main class="login-shell">
    <section class="login-hero" aria-label="后台登录介绍">
      <div class="login-brand">
        <div class="brand-mark">CM</div>
        <div>
          <p class="eyebrow">CampusMind Console</p>
          <h1>校园事件审核中枢</h1>
        </div>
      </div>

      <div class="login-statement">
        <p class="eyebrow">Admin Access</p>
        <h2>把分散的校园通知，压缩成一条可审核的事件流。</h2>
      </div>

      <div class="login-matrix" aria-label="登录页能力概览">
        <article>
          <span>01</span>
          <strong>采集源巡检</strong>
          <small>公开网页、雨课堂、用户导入统一进入后台队列。</small>
        </article>
        <article>
          <span>02</span>
          <strong>Agent 抽取</strong>
          <small>识别时间、地点、对象与发布风险。</small>
        </article>
        <article>
          <span>03</span>
          <strong>人工复核</strong>
          <small>确认发布、驳回、纠错全部留痕。</small>
        </article>
      </div>
    </section>

    <section class="login-panel" aria-label="管理员登录">
      <form class="login-form" @submit.prevent="submitLogin">
        <div>
          <p class="eyebrow">Secure Gate</p>
          <h3>管理员登录</h3>
        </div>

        <label>
          <span>账号</span>
          <input v-model="username" autocomplete="username" type="text" placeholder="admin_seed" />
        </label>

        <label>
          <span>密码</span>
          <input v-model="password" autocomplete="current-password" type="password" placeholder="输入管理员密码" />
        </label>

        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <button class="solid-button login-submit" type="submit" :disabled="!canSubmit">
          {{ loading ? '正在校验' : '登录后台' }}
        </button>
        <button class="ghost-button login-demo" type="button" @click="enterDemo">进入演示后台</button>
      </form>

      <div class="login-footnote">
        <span>JWT</span>
        <span>RBAC</span>
        <span>Audit Trail</span>
      </div>
    </section>
  </main>
</template>
