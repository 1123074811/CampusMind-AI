<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { AdminManagedUser } from '../adminTypes';

const props = defineProps<{
  users: AdminManagedUser[];
  loading: boolean;
}>();

const emit = defineEmits<{
  refresh: [];
  create: [payload: { username: string; phone: string; role: string; password: string }];
  toggle: [user: AdminManagedUser];
  resetPassword: [user: AdminManagedUser];
}>();

const roleFilter = ref<'ALL' | 'ADMIN' | 'OPERATOR' | 'STUDENT'>('ALL');
const form = reactive({
  username: '',
  phone: '',
  role: 'STUDENT',
  password: '123456'
});

const filteredUsers = computed(() => {
  if (roleFilter.value === 'ALL') {
    return props.users;
  }
  return props.users.filter((user) => user.role === roleFilter.value);
});

function submitCreate() {
  if (!form.username.trim() || !form.password.trim()) {
    return;
  }
  emit('create', {
    username: form.username.trim(),
    phone: form.phone.trim(),
    role: form.role,
    password: form.password
  });
  form.username = '';
  form.phone = '';
  form.role = 'STUDENT';
  form.password = '123456';
}

function roleLabel(role: string) {
  return { ADMIN: '管理员', OPERATOR: '运营人员', STUDENT: '学生' }[role] ?? role;
}
</script>

<template>
  <section class="split-workspace">
    <section class="source-board" aria-label="用户列表">
      <div class="panel-head">
        <div>
          <p class="eyebrow">User Directory</p>
          <h3>用户管理</h3>
        </div>
        <div class="segmented-control" aria-label="角色过滤">
          <button type="button" :class="{ active: roleFilter === 'ALL' }" @click="roleFilter = 'ALL'">全部</button>
          <button type="button" :class="{ active: roleFilter === 'ADMIN' }" @click="roleFilter = 'ADMIN'">管理员</button>
          <button type="button" :class="{ active: roleFilter === 'OPERATOR' }" @click="roleFilter = 'OPERATOR'">运营</button>
          <button type="button" :class="{ active: roleFilter === 'STUDENT' }" @click="roleFilter = 'STUDENT'">学生</button>
        </div>
      </div>

      <ul class="timeline-list">
        <li v-for="user in filteredUsers" :key="user.id">
          <time>#{{ user.id }}</time>
          <div>
            <strong>{{ user.username }}</strong>
            <small>{{ user.phone || '未填写手机号' }} · {{ roleLabel(user.role) }}</small>
          </div>
          <StatusPill :status="user.status === 1 ? 'HEALTHY' : 'PAUSED'" />
        </li>
      </ul>
    </section>

    <aside class="inspector-panel" aria-label="用户操作">
      <p class="eyebrow">Create Account</p>
      <h3>新建用户</h3>
      <div class="filters vertical">
        <input v-model="form.username" type="text" placeholder="账号" />
        <input v-model="form.phone" type="text" placeholder="手机号" />
        <select v-model="form.role">
          <option value="STUDENT">学生</option>
          <option value="OPERATOR">运营</option>
          <option value="ADMIN">管理员</option>
        </select>
        <input v-model="form.password" type="text" placeholder="初始密码" />
      </div>
      <button type="button" class="solid-button" :disabled="props.loading" @click="submitCreate">创建用户</button>

      <p class="eyebrow">Quick Actions</p>
      <dl class="stacked-list">
        <div v-for="user in filteredUsers.slice(0, 6)" :key="user.id">
          <dt>{{ user.username }} · {{ roleLabel(user.role) }}</dt>
          <dd class="mini-actions">
            <button type="button" class="ghost-button tiny" @click="emit('toggle', user)">
              {{ user.status === 1 ? '禁用' : '启用' }}
            </button>
            <button type="button" class="ghost-button tiny" @click="emit('resetPassword', user)">重置密码</button>
          </dd>
        </div>
      </dl>
      <button type="button" class="ghost-button" @click="emit('refresh')">刷新用户</button>
    </aside>
  </section>
</template>
