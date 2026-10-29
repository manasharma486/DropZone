/* ═══════════════════════════════════════════════════════════
   DROPZONE — Frontend Application
   Full-featured file manager: upload, download, preview,
   folders, breadcrumbs, search, sort, filter, trash, 
   move, copy, rename, drag-drop, grid/list toggle
   ═══════════════════════════════════════════════════════════ */
(() => {
  'use strict';

  const API = '/api';
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  /* ─── State ─── */
  const state = {
    token: localStorage.getItem('dropzone.token'),
    view: 'drive',
    filter: 'all',
    folderId: null,
    listing: null,
    rows: [],
    bin: [],
    search: '',
    sort: 'name',
    page: 0,
    layout: 'list',
    menuItem: null,
    authMode: 'login',
    user: null,
  };

  /* ─── DOM refs ─── */
  const els = {
    app:            $('#app'),
    auth:           $('#auth-view'),
    authForm:       $('#auth-form'),
    authError:      $('#auth-error'),
    rows:           $('#file-rows'),
    empty:          $('#empty-state'),
    list:           $('#file-list'),
    modal:          $('#action-modal'),
    modalForm:      $('#modal-form'),
    modalFields:    $('#modal-fields'),
    context:        $('#context-menu'),
    fileInput:      $('#file-input'),
    toast:          $('#toast-region'),
    uploadProgress: $('#upload-progress'),
  };

  let searchTimer;

  /* ═══════════════════════════════════════════════════════════
     UTILITIES
     ═══════════════════════════════════════════════════════════ */

  function toast(message, kind = '') {
    const node = document.createElement('div');
    node.className = `toast ${kind}`;
    node.textContent = message;
    els.toast.append(node);
    setTimeout(() => node.remove(), 4000);
  }

  function escapeHtml(v = '') {
    return String(v).replace(/[&<>"']/g, c =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
    );
  }

  function formatBytes(v) {
    if (typeof v === 'string' && !/^\d+(\.\d+)?$/.test(v.trim())) return v;
    const n = Number(v || 0);
    if (!n) return '0 B';
    const unit = Math.min(4, Math.floor(Math.log(n) / Math.log(1024)));
    return `${(n / 1024 ** unit).toFixed(unit === 0 ? 0 : 1)} ${['B', 'KB', 'MB', 'GB', 'TB'][unit]}`;
  }

  function formatDate(v) {
    if (!v) return '—';
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return '—';
    const now = new Date();
    if (d.toDateString() === now.toDateString())
      return `Today, ${d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`;
    const diff = Math.floor((new Date(now.toDateString()) - new Date(d.toDateString())) / 86400000);
    if (diff === 1) return 'Yesterday';
    return d.toLocaleDateString([], {
      month: 'short', day: 'numeric',
      year: d.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
    });
  }

  function bytesFromSize(s) {
    if (typeof s === 'number') return s;
    const m = String(s || '').match(/([\d.]+)\s*(B|KB|MB|GB|TB)?/i);
    if (!m) return 0;
    const u = (m[2] || 'B').toUpperCase();
    const idx = ['B', 'KB', 'MB', 'GB', 'TB'].indexOf(u);
    return Number(m[1]) * (idx < 0 ? 1 : 1024 ** idx);
  }

  /** Normalize backend FileResponse / StoredFileDto into uniform shape */
  function normalizeFile(f) {
    return {
      id: f.id || f.fileId,
      name: f.name || f.fileName || 'Untitled',
      size: f.sizeBytes ?? f.fileSize ?? 0,
      contentType: f.contentType || '',
      date: f.uploadedAt || f.uploaded_at || f.updatedAt || '',
      status: f.status || '',
    };
  }

  /** Normalize backend FolderResponse / StoredFolderDto */
  function normalizeFolder(f) {
    return {
      id: f.id || f.folderId,
      name: f.name || f.folderName || 'Untitled folder',
      isFolder: true,
      date: f.updatedAt || f.createdAt || '',
    };
  }

  /* ═══════════════════════════════════════════════════════════
     API REQUEST HELPER
     ═══════════════════════════════════════════════════════════ */

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) headers.set('Authorization', `Bearer ${state.token}`);
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type'))
      headers.set('Content-Type', 'application/json');

    let response;
    try {
      response = await fetch(API + path, { ...options, headers });
    } catch {
      throw new Error('Could not reach Dropzone. Check your connection and try again.');
    }

    if (response.status === 401) {
      if (state.token) {
        state.token = null;
        localStorage.removeItem('dropzone.token');
        showAuth('login');
      }
      throw new Error('Please sign in to continue.');
    }

    if (!response.ok) {
      let message = `Request failed (${response.status})`;
      try {
        const body = await response.json();
        if (body.fieldErrors && Array.isArray(body.fieldErrors) && body.fieldErrors.length > 0) {
          message = body.fieldErrors.map(e => e.message || `${e.field}: invalid`).join(', ');
        } else {
          message = body.message || body.detail || body.error || message;
        }
      } catch {
        try { const t = await response.text(); if (t) message = t; } catch {}
      }
      throw new Error(message);
    }

    if (response.status === 204) return null;
    const type = response.headers.get('content-type') || '';
    return type.includes('application/json') ? response.json() : response;
  }

  /* ═══════════════════════════════════════════════════════════
     AUTH
     ═══════════════════════════════════════════════════════════ */

  function decodeToken() {
    try {
      return JSON.parse(atob(state.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    } catch { return {}; }
  }

  function showAuth(mode = 'login') {
    state.authMode = mode;
    els.auth.hidden = false;
    els.app.hidden = true;
    const reg = mode === 'register';
    $('#auth-kicker').textContent = reg ? 'GET STARTED' : 'WELCOME BACK';
    $('#auth-title').textContent = reg ? 'Create your space' : 'Sign in to Dropzone';
    $('#auth-subtitle').textContent = reg
      ? 'A little order starts with an account.'
      : 'Pick up right where you left off.';
    $('#name-field').hidden = !reg;
    $('[name=name]', els.authForm).required = reg;
    $('[name=password]', els.authForm).autocomplete = reg ? 'new-password' : 'current-password';
    $('#auth-submit-label').textContent = reg ? 'Create account' : 'Sign in';
    $('#auth-switch-copy').textContent = reg ? 'Already have an account?' : 'New to Dropzone?';
    $('#auth-switch').textContent = reg ? 'Sign in' : 'Create an account';
    els.authError.textContent = '';
  }

  async function handleAuth(event) {
    event.preventDefault();
    els.authError.textContent = '';
    const data = Object.fromEntries(new FormData(els.authForm).entries());
    try {
      if (state.authMode === 'register') {
        await request('/users/signup', {
          method: 'POST',
          body: JSON.stringify({ name: data.name, email: data.email, password: data.password }),
        });
      }
      const result = await request('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: data.email, password: data.password }),
      });
      state.token = result.accessToken;
      localStorage.setItem('dropzone.token', state.token);
      await boot();
    } catch (error) {
      els.authError.textContent = error.message;
    }
  }

  /* ═══════════════════════════════════════════════════════════
     BOOT
     ═══════════════════════════════════════════════════════════ */

  function updateStorageQuota(user) {
    if (!user) return;
    const usedBytes = user.usedBytes ?? 0;
    const quotaBytes = user.quotaBytes ?? (1024 * 1024 * 1024);
    const used = user.formattedUsed || formatBytes(usedBytes);
    const quota = user.formattedQuota || formatBytes(quotaBytes);
    const pct = quotaBytes > 0 ? Math.min(100, Math.round((usedBytes / quotaBytes) * 100)) : 0;

    const labelTitle = $('#storage-label-title');
    if (labelTitle) labelTitle.textContent = 'Storage';
    const textEl = $('#storage-text');
    if (textEl) textEl.textContent = `${pct}%`;
    const bar = $('#storage-bar-fill');
    if (bar) bar.style.width = `${pct}%`;
    const captionEl = $('#storage-caption');
    if (captionEl) captionEl.textContent = `${used} of ${quota} used`;
  }

  async function refreshUser() {
    if (!state.token) return;
    try {
      const user = await request('/users/me');
      if (user) {
        state.user = { ...state.user, ...user };
        const display = user.name || (user.email && user.email.includes('@') ? user.email.split('@')[0] : 'Your account');
        $('#profile-name').textContent = display;
        $('#profile-email').textContent = user.email || '';
        const initial = (display[0] || 'D').toUpperCase();
        $('#profile-avatar').textContent = initial;
        $('#workspace-avatar').textContent = initial;
        $('#workspace-name').textContent = `${display}'s Dropzone`;
        updateStorageQuota(user);
      }
    } catch {
      // Keep existing claims
    }
  }

  async function boot() {
    if (!state.token) return showAuth('login');
    els.auth.hidden = true;
    els.app.hidden = false;

    const claims = decodeToken();
    const email = claims.email || claims.sub || claims.username || '';
    const display = claims.name || (email.includes('@') ? email.split('@')[0] : 'Your account');
    state.user = { email, display };

    $('#profile-name').textContent = display;
    $('#profile-email').textContent = email;
    const initial = (display[0] || 'D').toUpperCase();
    $('#profile-avatar').textContent = initial;
    $('#workspace-avatar').textContent = initial;
    $('#workspace-name').textContent = `${display}'s Dropzone`;

    refreshUser();

    try { await loadView(); }
    catch (error) { toast(error.message, 'error'); }
  }

  /* ═══════════════════════════════════════════════════════════
     DATA LOADING
     ═══════════════════════════════════════════════════════════ */

  async function loadView() {
    closeMenu();
    refreshUser();

    if (state.view === 'trash') return loadTrash();

    let data;

    // Search mode
    if (state.search.trim()) {
      data = await request(`/files/search?q=${encodeURIComponent(state.search.trim())}`);
      state.rows = Array.isArray(data) ? data.map(normalizeFile) : [];
      state.listing = null;
    }
    // Recent mode — get all files and sort by date
    else if (state.view === 'recent') {
      data = await request('/files');
      state.listing = null;
      state.rows = (Array.isArray(data) ? data : [])
        .map(normalizeFile)
        .sort((a, b) => new Date(b.date) - new Date(a.date));
    }
    // Drive mode — use the /drive endpoint with pagination
    else {
      const params = new URLSearchParams({
        sortBy: state.sort === 'uploadedAt' ? 'date' : state.sort === 'size' ? 'name' : state.sort,
        order: state.sort === 'uploadedAt' ? 'desc' : 'asc',
        size: '100',
        page: String(state.page),
      });
      if (state.folderId) params.set('folderId', state.folderId);
      data = await request(`/drive?${params}`);
      state.listing = data;
      state.rows = [
        ...(state.page === 0 ? (data.folders || []).map(normalizeFolder) : []),
        ...(data.files || []).map(normalizeFile),
      ];
    }

    // Client-side sort by size
    if (state.sort === 'size')
      state.rows.sort((a, b) => (b.isFolder ? -1 : bytesFromSize(b.size)) - (a.isFolder ? -1 : bytesFromSize(a.size)));

    render();
  }

  async function loadTrash() {
    const [fileData, folderData] = await Promise.all([
      request('/bin').catch(() => []),
      request('/folders?status=DELETED').catch(() => []),
    ]);
    const files = (Array.isArray(fileData) ? fileData : []).map(normalizeFile);
    const folders = (Array.isArray(folderData) ? folderData : []).map(normalizeFolder);
    state.bin = [...folders, ...files];
    state.rows = state.bin.map(item => ({ ...item, isTrash: true }));
    state.listing = null;
    render();
  }

  async function refreshAfterMutation() {
    if (state.view === 'drive' && !state.search) {
      state.folderId = state.listing?.currentFolderId || null;
    }
    try {
      await loadView();
    } catch (error) {
      console.warn('The action succeeded, but the current view could not be refreshed.', error);
      toast('The action completed, but this view could not refresh. Reload to see the latest contents.', 'error');
    }
  }

  /* ═══════════════════════════════════════════════════════════
     RENDER
     ═══════════════════════════════════════════════════════════ */

  function render() {
    const trash = state.view === 'trash';

    els.list.hidden = false;
    els.empty.hidden = true;
    $('#heading-actions').hidden = trash;
    $('#bin-actions').hidden = !trash;
    $('#section-toolbar').hidden = trash;

    // Page heading
    $('#page-context').textContent =
      trash ? 'YOUR LIBRARY'
      : state.view === 'recent' ? 'YOUR LIBRARY'
      : state.search ? 'SEARCH RESULTS'
      : 'YOUR WORKSPACE';

    $('#page-title').textContent =
      trash ? 'Trash'
      : state.view === 'recent' ? 'Recent'
      : state.search ? 'Search results'
      : (state.listing?.currentFolderName || (state.folderId ? 'Folder' : 'My files'));

    $('#breadcrumbs').innerHTML = renderBreadcrumbs();

    // Filter rows
    const filtered = state.rows.filter(item => {
      if (state.filter === 'folders') return item.isFolder;
      if (state.filter === 'documents')
        return !item.isFolder && /pdf|document|text|word|sheet|presentation|csv/i.test(`${item.contentType} ${item.name}`);
      if (state.filter === 'images')
        return !item.isFolder && (/image/i.test(item.contentType || '') || /\.(png|jpe?g|gif|webp|svg|heic)$/i.test(item.name));
      return true;
    });

    // Stats
    const folders = state.rows.filter(i => i.isFolder).length;
    const fileCount = state.rows.filter(i => !i.isFolder).length;
    const totalSize = state.rows.filter(i => !i.isFolder).reduce((sum, i) => sum + bytesFromSize(i.size), 0);

    $('#file-count').textContent = state.search ? `${fileCount}` : state.listing?.totalFiles ?? fileCount;
    $('#folder-count').textContent = state.search ? '—' : state.listing?.totalFolders ?? folders;
    $('#overview-total').textContent = totalSize ? formatBytes(totalSize) : '0 B';
    $('#all-count').textContent = String(state.rows.length);
    $('#footer-count').textContent = `${filtered.length} ${filtered.length === 1 ? 'item' : 'items'}`;
    if (state.user?.quotaBytes) {
      updateStorageQuota(state.user);
    } else {
      $('#storage-text').textContent = formatBytes(totalSize);
    }

    // Render file rows
    $('#file-rows').innerHTML = filtered.map(rowMarkup).join('');

    // Pagination
    const pageCount = state.listing?.totalPages || 0;
    $('#pagination').hidden = !state.listing || pageCount < 2;
    $('#page-previous').disabled = !state.listing || state.page <= 0;
    $('#page-next').disabled = !state.listing || state.page >= pageCount - 1;
    $('#page-status').textContent = state.listing ? `Page ${state.page + 1} of ${Math.max(1, pageCount)}` : '';

    // Empty state
    $('#empty-title').textContent =
      trash ? 'Your trash is empty.'
      : state.search ? 'No files found.'
      : state.view === 'recent' ? 'Nothing recent yet.'
      : state.filter === 'folders' ? 'No folders here yet.'
      : 'A little room to begin.';

    $('#empty-copy').textContent =
      trash ? 'Deleted files will show up here. You can restore them or remove them permanently.'
      : state.search ? `Try a different name or search term for "${escapeHtml(state.search)}".`
      : 'Drop in a file or create a folder. Your space is ready.';

    $('#empty-create').hidden = trash || state.search.length > 0 || state.filter === 'images' || state.filter === 'documents';

    els.list.hidden = filtered.length === 0;
    els.empty.hidden = filtered.length > 0;

    setActiveNav();

    $$('.view-tab').forEach(tab => {
      tab.classList.toggle('active', tab.dataset.filter === state.filter);
      tab.setAttribute('aria-selected', tab.dataset.filter === state.filter ? 'true' : 'false');
    });
  }

  /* ─── Breadcrumbs ─── */
  function renderBreadcrumbs() {
    if (state.search)
      return `<button type="button" data-root>My files</button><span class="crumb-sep">/</span><span>Search · ${escapeHtml(state.search)}</span>`;
    if (state.view === 'trash')
      return '<span>Files and folders you\'ve deleted.</span>';

    const rawCrumbs = state.listing?.breadcrumbs || [];
    const crumbs = rawCrumbs.filter(c => c && c.id);
    if (!state.folderId || !crumbs.length)
      return '<span>Everything you\'ve stored, together.</span>';

    return `<button type="button" data-root>My files</button>${crumbs.map((item, i) =>
      `<span class="crumb-sep">/</span>${i === crumbs.length - 1
        ? `<span>${escapeHtml(item.name || item.folderName || 'Folder')}</span>`
        : `<button type="button" data-folder="${escapeHtml(item.id || item.folderId)}">${escapeHtml(item.name || item.folderName || 'Folder')}</button>`
      }`
    ).join('')}`;
  }

  /* ─── Active nav ─── */
  function setActiveNav() {
    $$('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === state.view));
  }

  /* ─── Type helpers ─── */
  function typeLabel(item) {
    if (item.isFolder) return 'Folder';
    const ext = item.name.includes('.') ? item.name.split('.').pop().toUpperCase() : '';
    const known = {
      PDF:'PDF',PNG:'PNG',JPG:'JPEG',JPEG:'JPEG',GIF:'GIF',WEBP:'WEBP',SVG:'SVG',
      DOC:'Word',DOCX:'Word',XLS:'Excel',XLSX:'Excel',PPT:'Slides',PPTX:'Slides',
      CSV:'CSV',TXT:'Text',ZIP:'Archive',MP3:'Audio',MP4:'Video',MOV:'Video',
      RAR:'Archive','7Z':'Archive',TAR:'Archive',GZ:'Archive',
    };
    return known[ext] || (ext ? `${ext} file` : 'File');
  }

  function glyph(item) {
    if (item.isFolder) return ['folder', 'DIR'];
    const ext = item.name.includes('.') ? item.name.split('.').pop().toLowerCase() : '';
    let kind = 'file';
    let label = (ext || 'FILE').slice(0, 4).toUpperCase();
    if (/pdf/.test(ext) || /pdf/i.test(item.contentType)) kind = 'pdf';
    else if (/png|jpe?g|gif|webp|svg|heic/.test(ext) || /image/i.test(item.contentType)) kind = 'image';
    else if (/zip|rar|7z|tar|gz/.test(ext)) kind = 'archive';
    else if (/mp3|wav|aac|flac|ogg/.test(ext)) kind = 'audio';
    else if (/mp4|mov|avi|webm|mkv/.test(ext)) kind = 'video';
    return [kind, label];
  }

  /* ─── Row Markup ─── */
  function rowMarkup(item) {
    const [kind, label] = glyph(item);
    const id = escapeHtml(item.id || '');
    const date = formatDate(item.date);
    return `<article class="file-row ${item.isFolder ? 'is-folder' : 'is-file'} ${item.isTrash ? 'trash-row' : ''}"
      data-id="${id}" data-is-folder="${item.isFolder ? 'true' : 'false'}"
      draggable="${!item.isTrash}"
      tabindex="${item.isFolder ? '0' : '-1'}" ${item.isFolder ? 'role="button"' : ''}>
      <div class="file-name-cell">
        <span class="file-glyph ${kind}" aria-hidden="true">${label}</span>
        <div style="min-width:0">
          <div class="file-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>
          <div class="file-subtitle">${escapeHtml(typeLabel(item))}</div>
        </div>
      </div>
      <span class="file-kind">${escapeHtml(typeLabel(item))}</span>
      <span class="file-size">${item.isFolder ? '—' : formatBytes(item.size)}</span>
      <span class="file-date">${escapeHtml(date)}</span>
      <div class="row-actions-cell">
        ${item.isTrash
          ? `<button class="row-action-button" type="button" aria-label="Restore ${escapeHtml(item.name)}" data-restore="${id}" title="Restore">
               <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" width="14" height="14"><path d="M2 8a6 6 0 0 1 10.2-4.2L14 6M14 2v4h-4"/><path d="M14 8a6 6 0 0 1-10.2 4.2L2 10M2 14v-4h4"/></svg>
             </button>`
          : `<button class="row-action-button" type="button" aria-label="Download ${escapeHtml(item.name)}" data-download="${id}" title="${item.isFolder ? 'Download as ZIP' : 'Download file'}">
               <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" width="14" height="14"><path d="M8 2v8M4 7l4 4 4-4"/><path d="M2 13h12"/></svg>
             </button>`
        }
        <button class="row-menu-button" type="button" aria-label="Actions for ${escapeHtml(item.name)}" data-menu="${id}">···</button>
      </div>
    </article>`;
  }

  /* ═══════════════════════════════════════════════════════════
     ACTIONS
     ═══════════════════════════════════════════════════════════ */

  async function createFolder(name) {
    // The drive response is authoritative; a UI flag or stale route value must
    // never be sent as a parent folder ID.
    const parentId = state.listing?.currentFolderId || null;
    if (!name) throw new Error('Folder name cannot be empty.');
    await request('/folders', {
      method: 'POST',
      body: JSON.stringify({ name, parentFolderId: parentId }),
    });
    toast(`"${name}" is ready.`, 'success');
    await loadView();
  }

  async function uploadFiles(files) {
    if (!files?.length) return;
    const form = new FormData();
    [...files].forEach(f => form.append('file', f));
    if (state.folderId && state.folderId !== 'null' && state.folderId !== 'undefined' && String(state.folderId).trim() !== '') {
      form.append('folderId', String(state.folderId).trim());
    }

    els.uploadProgress.hidden = false;
    $('#upload-progress-label').textContent = `Uploading ${files.length} ${files.length === 1 ? 'file' : 'files'}…`;

    try {
      await request('/files/upload', { method: 'POST', body: form });
      toast(`${files.length === 1 ? 'File' : 'Files'} uploaded.`, 'success');
      await loadView();
    } catch (error) {
      toast(error.message, 'error');
    } finally {
      els.uploadProgress.hidden = true;
      els.fileInput.value = '';
    }
  }

  /* ─── Modal ─── */
  function openModal({ eyebrow, title, description, fields, submit, onSubmit }) {
    $('#modal-eyebrow').textContent = eyebrow;
    $('#modal-title').textContent = title;
    $('#modal-description').textContent = description || '';
    els.modalFields.innerHTML = fields || '';
    $('#modal-submit').textContent = submit || 'Save';

    // Cancel buttons
    $$('[value="cancel"]', els.modalForm).forEach(btn => {
      btn.onclick = () => els.modal.close();
    });

    els.modalForm.onsubmit = async event => {
      event.preventDefault();
      const button = $('#modal-submit');
      button.disabled = true;
      try {
        const values = Object.fromEntries(new FormData(els.modalForm).entries());
        await onSubmit(values);
        els.modal.close();
      } catch (error) {
        toast(error.message, 'error');
      } finally {
        button.disabled = false;
      }
    };

    els.modal.showModal();
    const input = $('input', els.modalFields);
    if (input) setTimeout(() => input.focus(), 50);
  }

  function openFolderModal() {
    const currentName = state.listing?.currentFolderName || (state.folderId ? 'current folder' : 'root');
    const isRoot = !state.folderId;
    openModal({
      eyebrow: 'NEW FOLDER',
      title: isRoot ? 'Create a folder' : `New folder in "${currentName}"`,
      description: isRoot ? 'Keep related files together.' : `This folder will be created inside "${currentName}".`,
      fields: '<label>Folder name<input name="name" maxlength="255" placeholder="e.g. Project notes" required autofocus></label>',
      submit: 'Create folder',
      onSubmit: async values => createFolder(values.name.trim()),
    });
  }

  async function openDestinationModal(item, action) {
    const folders = await request('/folders');
    const list = Array.isArray(folders) ? folders : [];
    const byId = new Map(list.map(f => [String(f.id || f.folderId), f]));

    function path(f) {
      const names = [f.name || f.folderName || 'Folder'];
      let parent = f.parentFolderId || f.parentFolder;
      const seen = new Set();
      while (parent && byId.has(String(parent)) && !seen.has(String(parent))) {
        seen.add(String(parent));
        const row = byId.get(String(parent));
        names.unshift(row.name || row.folderName || 'Folder');
        parent = row.parentFolderId || row.parentFolder;
      }
      return names.join(' / ');
    }

    const options = list
      .filter(f => !(action === 'move' && String(f.id || f.folderId) === String(state.folderId)))
      .sort((a, b) => path(a).localeCompare(path(b)))
      .map(f => `<option value="${escapeHtml(f.id || f.folderId)}">${escapeHtml(path(f))}</option>`)
      .join('');

    openModal({
      eyebrow: action === 'move' ? 'MOVE FILE' : 'COPY FILE',
      title: action === 'move' ? 'Choose a destination' : 'Choose where to place the copy',
      description: action === 'move'
        ? 'The file will leave its current folder.'
        : 'The original file will stay where it is.',
      fields: `<label>Destination<select name="destinationFolderId"><option value="">My files (root)</option>${options}</select></label>`,
      submit: action === 'move' ? 'Move file' : 'Copy file',
      onSubmit: async values => {
        const folderId = values.destinationFolderId || null;
        await request(
          `/files/${encodeURIComponent(item.id)}${action === 'move' ? '/move' : '/copy'}`,
          {
            method: action === 'move' ? 'PATCH' : 'POST',
            body: JSON.stringify({ destinationFolderId: folderId }),
          }
        );
        toast(action === 'move' ? 'File moved.' : 'File copied.', 'success');
        await refreshAfterMutation();
      },
    });
  }

  async function openMoveFolderModal(item) {
    const folders = await request('/folders');
    const list = Array.isArray(folders) ? folders : [];
    const byId = new Map(list.map(f => [String(f.id || f.folderId), f]));

    function path(f) {
      const names = [f.name || f.folderName || 'Folder'];
      let parent = f.parentFolderId || f.parentFolder;
      const seen = new Set();
      while (parent && byId.has(String(parent)) && !seen.has(String(parent))) {
        seen.add(String(parent));
        const row = byId.get(String(parent));
        names.unshift(row.name || row.folderName || 'Folder');
        parent = row.parentFolderId || row.parentFolder;
      }
      return names.join(' / ');
    }

    // A folder cannot be moved into itself or any folder below it.
    function isDescendantOf(candidate, ancestorId) {
      let parent = candidate.parentFolderId || candidate.parentFolder;
      const seen = new Set();
      while (parent && !seen.has(String(parent))) {
        if (String(parent) === String(ancestorId)) return true;
        seen.add(String(parent));
        const parentFolder = byId.get(String(parent));
        parent = parentFolder && (parentFolder.parentFolderId || parentFolder.parentFolder);
      }
      return false;
    }

    const options = list
      .filter(f => String(f.id || f.folderId) !== String(item.id) && !isDescendantOf(f, item.id))
      .sort((a, b) => path(a).localeCompare(path(b)))
      .map(f => `<option value="${escapeHtml(f.id || f.folderId)}">${escapeHtml(path(f))}</option>`)
      .join('');

    openModal({
      eyebrow: 'MOVE FOLDER',
      title: `Move "${item.name}"`,
      description: 'Choose a new parent folder.',
      fields: `<label>Destination<select name="destinationFolderId"><option value="">My files (root)</option>${options}</select></label>`,
      submit: 'Move folder',
      onSubmit: async values => {
        const destinationFolderId = values.destinationFolderId || null;
        await request(`/folders/${encodeURIComponent(item.id)}/move`, {
          method: 'PATCH',
          body: JSON.stringify({ destinationFolderId }),
        });
        toast('Folder moved.', 'success');
        await refreshAfterMutation();
      },
    });
  }

  /* ─── Dedicated Binary / Archive Downloader ─── */
  async function downloadItem(item) {
    const isFolder = !!item.isFolder;
    const url = `${API}/${isFolder ? 'folders' : 'files'}/${encodeURIComponent(item.id)}/download`;

    toast(`Preparing ${isFolder ? 'folder download' : 'file download'}…`);

    const res = await fetch(url, {
      headers: state.token ? { Authorization: `Bearer ${state.token}` } : {},
    });

    if (!res.ok) {
      let msg = `Download failed (${res.status})`;
      try {
        const body = await res.json();
        msg = body.message || body.detail || msg;
      } catch {}
      throw new Error(msg);
    }

    const blob = await res.blob();
    const blobUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');

    const disposition = res.headers.get('content-disposition') || '';
    const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename=\"?([^;"]+)\"?/i);
    let filename = item.name || (isFolder ? 'folder' : 'download');
    if (match) {
      try {
        filename = decodeURIComponent(match[1] || match[2]);
      } catch {
        filename = match[1] || match[2];
      }
    } else if (isFolder && !filename.toLowerCase().endsWith('.zip')) {
      filename += '.zip';
    }

    anchor.href = blobUrl;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
    toast(`Downloaded "${filename}".`, 'success');
  }

  /* ─── Context menu ─── */
  function showMenuAt(clientX, clientY, item) {
    state.menuItem = item;
    const trash = !!item.isTrash;

    const actions = trash
      ? [['restore', 'Restore'], ['permanent', 'Delete permanently', 'danger']]
      : item.isFolder
        ? [
            ['open', 'Open folder'],
            ['download', 'Download as ZIP'],
            ['rename', 'Rename'],
            ['move-folder', 'Move folder'],
            ['delete', 'Move to trash', 'danger'],
          ]
        : [
            ['preview', 'Open file'],
            ['download', 'Download'],
            ['move', 'Move to folder'],
            ['copy', 'Copy to folder'],
            ['rename', 'Rename'],
            ['delete', 'Move to trash', 'danger'],
          ];

    els.context.innerHTML = actions.map((action, i) =>
      `${i > 0 && (action[2] === 'danger') ? '<div class="menu-rule"></div>' : ''}<button type="button" data-action="${action[0]}" class="${action[2] || ''}">${action[1]}</button>`
    ).join('');

    els.context.hidden = false;

    const width = 190;
    const height = els.context.offsetHeight || 160;
    const left = Math.min(window.innerWidth - width - 12, Math.max(12, clientX - (width / 2)));
    const top = Math.min(window.innerHeight - height - 12, Math.max(12, clientY + 4));
    els.context.style.left = `${left}px`;
    els.context.style.top = `${top}px`;
  }

  function showMenu(button, item) {
    state.menuItem = item;
    const trash = !!item.isTrash;

    const actions = trash
      ? [['restore', 'Restore'], ['permanent', 'Delete permanently', 'danger']]
      : item.isFolder
        ? [
            ['open', 'Open folder'],
            ['download', 'Download as ZIP'],
            ['rename', 'Rename'],
            ['move-folder', 'Move folder'],
            ['delete', 'Move to trash', 'danger'],
          ]
        : [
            ['preview', 'Open file'],
            ['download', 'Download'],
            ['move', 'Move to folder'],
            ['copy', 'Copy to folder'],
            ['rename', 'Rename'],
            ['delete', 'Move to trash', 'danger'],
          ];

    els.context.innerHTML = actions.map((action, i) =>
      `${i > 0 && (action[2] === 'danger') ? '<div class="menu-rule"></div>' : ''}<button type="button" data-action="${action[0]}" class="${action[2] || ''}">${action[1]}</button>`
    ).join('');

    els.context.hidden = false;

    const rect = button.getBoundingClientRect();
    const width = 190;
    const height = els.context.offsetHeight || 160;
    const left = Math.min(window.innerWidth - width - 12, Math.max(12, rect.right - width));
    let top = rect.bottom + 4;
    if (top + height > window.innerHeight - 10) {
      top = Math.max(10, rect.top - height - 4);
    }
    els.context.style.left = `${left}px`;
    els.context.style.top = `${top}px`;
  }

  function closeMenu() {
    els.context.hidden = true;
    state.menuItem = null;
  }

  async function runAction(action, item) {
    closeMenu();

    if (action === 'open') return navigateFolder(item.id);

    if (action === 'move' || action === 'copy') return openDestinationModal(item, action);

    if (action === 'move-folder') return openMoveFolderModal(item);

    if (action === 'preview') {
      const preview = window.open('about:blank', '_blank');
      try {
        const res = await fetch(`${API}/files/${encodeURIComponent(item.id)}/download`, {
          headers: state.token ? { Authorization: `Bearer ${state.token}` } : {},
        });
        if (!res.ok) throw new Error(`Could not load preview (${res.status})`);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        if (preview) {
          preview.location.href = url;
          setTimeout(() => URL.revokeObjectURL(url), 60000);
        } else {
          URL.revokeObjectURL(url);
          toast('Your browser blocked the preview window. Allow pop-ups and try again.', 'error');
        }
      } catch (error) {
        preview?.close();
        throw error;
      }
      return;
    }

    if (action === 'download') {
      await downloadItem(item);
      return;
    }

    if (action === 'rename') {
      return openModal({
        eyebrow: 'RENAME ' + (item.isFolder ? 'FOLDER' : 'FILE'),
        title: 'Choose a new name',
        description: 'The contents stay right where they are.',
        fields: `<label>Name<input name="name" maxlength="255" value="${escapeHtml(item.name)}" required></label>`,
        submit: 'Save name',
        onSubmit: async values => {
          await request(`/${item.isFolder ? 'folders' : 'files'}/${encodeURIComponent(item.id)}`, {
            method: 'PATCH',
            body: JSON.stringify({ name: values.name.trim() }),
          });
          toast('Name updated.', 'success');
          await loadView();
        },
      });
    }

    if (action === 'delete') {
      if (!confirm(`Move "${item.name}" to trash?`)) return;
      await request(`/${item.isFolder ? 'folders' : 'files'}/${encodeURIComponent(item.id)}`, {
        method: 'DELETE',
      });
      toast(`"${item.name}" moved to trash.`, 'success');
      await loadView();
      return;
    }

    if (action === 'restore') {
      if (item.isFolder) {
        await request(`/folders/${encodeURIComponent(item.id)}/restore`, { method: 'POST' });
      } else {
        await request('/bin/restore', {
          method: 'POST',
          body: JSON.stringify({ fileIDs: [item.id] }),
        });
      }
      toast(`"${item.name}" restored.`, 'success');
      await loadView();
      return;
    }

    if (action === 'permanent') {
      if (!confirm(`Permanently delete "${item.name}"? This cannot be undone.`)) return;
      if (item.isFolder) {
        await request(`/folders/${encodeURIComponent(item.id)}/permanent`, { method: 'DELETE' });
      } else {
        await request('/bin/empty', {
          method: 'DELETE',
          body: JSON.stringify({ fileIDs: [item.id] }),
        });
      }
      toast(`"${item.name}" deleted permanently.`, 'success');
      await loadView();
    }
  }

  /* ─── Navigation ─── */
  async function navigateFolder(id) {
    state.view = 'drive';
    state.search = '';
    state.folderId = (id && id !== 'null' && id !== 'undefined' && String(id).trim() !== '')
      ? String(id).trim()
      : null;
    state.filter = 'all';
    state.page = 0;
    $('#search-input').value = '';
    await loadView();
  }

  /* ═══════════════════════════════════════════════════════════
     EVENT LISTENERS
     ═══════════════════════════════════════════════════════════ */

  // Auth
  els.authForm.addEventListener('submit', handleAuth);
  $('#auth-switch').addEventListener('click', () => showAuth(state.authMode === 'login' ? 'register' : 'login'));

  // Navigation
  $$('.nav-item').forEach(button =>
    button.addEventListener('click', async () => {
      state.view = button.dataset.view;
      state.folderId = null;
      state.search = '';
      state.filter = 'all';
      state.page = 0;
      $('#search-input').value = '';
      $('#heading-actions').hidden = false;
      $('#section-toolbar').hidden = false;
      try { await loadView(); }
      catch (e) { toast(e.message, 'error'); }
    })
  );

  // Filter tabs
  $$('.view-tab').forEach(button =>
    button.addEventListener('click', () => {
      state.filter = button.dataset.filter;
      render();
    })
  );

  // Create folder
  $('#new-folder').addEventListener('click', openFolderModal);
  $('#empty-create').addEventListener('click', openFolderModal);

  // Upload
  $('#upload-trigger').addEventListener('click', () => els.fileInput.click());
  els.fileInput.addEventListener('change', () => uploadFiles(els.fileInput.files));

  // Search
  $('#search-input').addEventListener('input', event => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(async () => {
      state.search = event.target.value;
      state.folderId = null;
      state.view = 'drive';
      state.page = 0;
      try { await loadView(); }
      catch (error) { toast(error.message, 'error'); }
    }, 300);
  });

  // Sort
  $('#sort-select').addEventListener('change', async event => {
    state.sort = event.target.value;
    state.page = 0;
    try { await loadView(); }
    catch (error) { toast(error.message, 'error'); }
  });
  $('#sort-select').addEventListener('change', () => $('#sort-select').blur());

  // Pagination
  $('#page-previous').addEventListener('click', async () => {
    if (state.page > 0) {
      state.page--;
      try { await loadView(); }
      catch (error) { toast(error.message, 'error'); }
    }
  });
  $('#page-next').addEventListener('click', async () => {
    if (state.page + 1 < (state.listing?.totalPages || 0)) {
      state.page++;
      try { await loadView(); }
      catch (error) { toast(error.message, 'error'); }
    }
  });

  // Layout toggle
  $('#layout-toggle').addEventListener('click', () => {
    state.layout = state.layout === 'list' ? 'grid' : 'list';
    els.list.classList.toggle('grid-layout', state.layout === 'grid');
    const btn = $('#layout-toggle');
    btn.innerHTML = state.layout === 'grid'
      ? '<svg viewBox="0 0 16 16" fill="currentColor"><rect x="1" y="1" width="14" height="3" rx="1"/><rect x="1" y="6" width="14" height="3" rx="1"/><rect x="1" y="11" width="14" height="3" rx="1"/></svg>'
      : '<svg viewBox="0 0 16 16" fill="currentColor"><rect x="1" y="1" width="6" height="6" rx="1"/><rect x="9" y="1" width="6" height="6" rx="1"/><rect x="1" y="9" width="6" height="6" rx="1"/><rect x="9" y="9" width="6" height="6" rx="1"/></svg>';
  });

  // File row clicks
  $('#file-rows').addEventListener('click', async event => {
    const downloadBtn = event.target.closest('[data-download]');
    if (downloadBtn) {
      event.stopPropagation();
      const row = downloadBtn.closest('.file-row');
      const item = state.rows.find(v => String(v.id) === row.dataset.id);
      if (item) {
        try { await downloadItem(item); } catch (e) { toast(e.message, 'error'); }
      }
      return;
    }
    const restoreBtn = event.target.closest('[data-restore]');
    if (restoreBtn) {
      event.stopPropagation();
      const row = restoreBtn.closest('.file-row');
      const item = state.bin.find(v => String(v.id) === row.dataset.id);
      if (item) {
        try { await runAction('restore', item); } catch (e) { toast(e.message, 'error'); }
      }
      return;
    }
    const menuButton = event.target.closest('[data-menu]');
    if (menuButton) {
      event.stopPropagation();
      const row = menuButton.closest('.file-row');
      const item = state.rows.find(v => String(v.id) === row.dataset.id)
        || state.bin.find(v => String(v.id) === row.dataset.id);
      if (item) showMenu(menuButton, item);
      return;
    }
    const row = event.target.closest('.file-row.is-folder');
    if (row && !event.target.closest('.row-menu-button') && !event.target.closest('.row-action-button')) {
      await navigateFolder(row.dataset.id);
    }
  });

  // Row Drag-and-Drop to move files/subfolders into folders
  let draggedRowData = null;

  $('#file-rows').addEventListener('dragstart', event => {
    const row = event.target.closest('.file-row');
    if (!row) return;
    const item = state.rows.find(v => String(v.id) === row.dataset.id);
    if (!item || item.isTrash) {
      event.preventDefault();
      return;
    }
    draggedRowData = { id: item.id, name: item.name, isFolder: !!item.isFolder };
    event.dataTransfer.setData('application/json', JSON.stringify(draggedRowData));
    event.dataTransfer.setData('text/plain', JSON.stringify(draggedRowData));
    event.dataTransfer.effectAllowed = 'move';
    row.classList.add('is-dragging');
  });

  $('#file-rows').addEventListener('dragend', () => {
    draggedRowData = null;
    $$('.file-row').forEach(r => r.classList.remove('is-dragging', 'drag-over'));
  });

  $('#file-rows').addEventListener('dragover', event => {
    if (!draggedRowData) return;
    const folderRow = event.target.closest('.file-row.is-folder');
    if (folderRow && folderRow.dataset.id !== String(draggedRowData.id)) {
      event.preventDefault();
      event.dataTransfer.dropEffect = 'move';
      folderRow.classList.add('drag-over');
    }
  });

  $('#file-rows').addEventListener('dragleave', event => {
    const folderRow = event.target.closest('.file-row.is-folder');
    if (folderRow && !folderRow.contains(event.relatedTarget)) {
      folderRow.classList.remove('drag-over');
    }
  });

  $('#file-rows').addEventListener('drop', async event => {
    let dragged = draggedRowData;
    if (!dragged) {
      try {
        dragged = JSON.parse(event.dataTransfer.getData('application/json')
          || event.dataTransfer.getData('text/plain'));
      } catch {
        return;
      }
    }
    const folderRow = event.target.closest('.file-row.is-folder');
    if (!folderRow || folderRow.dataset.id === String(dragged.id)) return;

    event.preventDefault();
    event.stopPropagation();
    folderRow.classList.remove('drag-over');

    const destFolderId = folderRow.dataset.id;
    draggedRowData = null;

    if (dragged.isFolder) {
      const allFolders = await request('/folders').catch(() => []);
      const byId = new Map((Array.isArray(allFolders) ? allFolders : [])
        .map(folder => [String(folder.id || folder.folderId), folder]));
      let parent = byId.get(String(destFolderId));
      const seen = new Set();
      while (parent && !seen.has(String(parent.id || parent.folderId))) {
        const parentId = String(parent.id || parent.folderId);
        if (parentId === String(dragged.id)) {
          toast('A folder cannot be moved inside itself.', 'error');
          return;
        }
        seen.add(parentId);
        const nextId = parent.parentFolderId || parent.parentFolder;
        parent = nextId ? byId.get(String(nextId)) : null;
      }
    }

    try {
      if (dragged.isFolder) {
        await request(`/folders/${encodeURIComponent(dragged.id)}/move`, {
          method: 'PATCH',
          body: JSON.stringify({ destinationFolderId: destFolderId }),
        });
      } else {
        await request(`/files/${encodeURIComponent(dragged.id)}/move`, {
          method: 'PATCH',
          body: JSON.stringify({ destinationFolderId: destFolderId }),
        });
      }
      toast(`Moved "${dragged.name}" into folder.`, 'success');
      await refreshAfterMutation();
    } catch (err) {
      toast(err.message, 'error');
    }
  });

  // Double click on file rows: open folder or download file
  $('#file-rows').addEventListener('dblclick', async event => {
    const row = event.target.closest('.file-row');
    if (!row || event.target.closest('.row-menu-button')) return;
    const item = state.rows.find(v => String(v.id) === row.dataset.id);
    if (!item || item.isTrash) return;
    if (item.isFolder) {
      await navigateFolder(item.id);
    } else {
      await downloadItem(item);
    }
  });

  // Right-click context menu
  $('#file-rows').addEventListener('contextmenu', event => {
    const row = event.target.closest('.file-row');
    if (row) {
      event.preventDefault();
      event.stopPropagation();
      const item = state.rows.find(v => String(v.id) === row.dataset.id)
        || state.bin.find(v => String(v.id) === row.dataset.id);
      if (item) showMenuAt(event.clientX, event.clientY, item);
    }
  });

  // Keyboard navigation for folders
  $('#file-rows').addEventListener('keydown', event => {
    if ((event.key === 'Enter' || event.key === ' ') && event.target.matches('.file-row.is-folder')) {
      event.preventDefault();
      navigateFolder(event.target.dataset.id);
    }
  });

  // Context menu actions
  els.context.addEventListener('click', async event => {
    event.stopPropagation();
    const button = event.target.closest('[data-action]');
    if (!button || !state.menuItem) return;
    const action = button.dataset.action;
    const item = state.menuItem;
    closeMenu();
    try { await runAction(action, item); }
    catch (error) { toast(error.message, 'error'); }
  });

  // Global clicks — close menu, breadcrumb navigation
  document.addEventListener('click', event => {
    if (!event.target.closest('#context-menu') && !event.target.closest('[data-menu]')) closeMenu();
    if (event.target.closest('[data-root]')) navigateFolder(null);
    const crumb = event.target.closest('[data-folder]');
    if (crumb) navigateFolder(crumb.dataset.folder);
  });

  // Global keyboard shortcuts
  document.addEventListener('keydown', event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      $('#search-input').focus();
    }
    if (event.key === 'Escape') closeMenu();
  });

  // Drag and drop
  const dropTarget = $('#drop-target');
  ['dragenter', 'dragover'].forEach(name =>
    document.addEventListener(name, event => {
      if (event.dataTransfer?.types.includes('Files')) {
        event.preventDefault();
        dropTarget.classList.add('visible', 'dragging');
      }
    })
  );
  ['dragleave', 'drop'].forEach(name =>
    document.addEventListener(name, event => {
      if (name === 'drop' && event.dataTransfer?.files?.length) {
        event.preventDefault();
        uploadFiles(event.dataTransfer.files);
      }
      dropTarget.classList.remove('dragging');
      if (name === 'drop') setTimeout(() => dropTarget.classList.remove('visible'), 700);
    })
  );

  // Mobile menu
  $('#mobile-menu').addEventListener('click', () => {
    $('#sidebar').classList.add('open');
    $('#mobile-scrim').classList.add('visible');
  });
  $('#mobile-scrim').addEventListener('click', () => {
    $('#sidebar').classList.remove('open');
    $('#mobile-scrim').classList.remove('visible');
  });
  $$('.nav-item').forEach(button =>
    button.addEventListener('click', () => {
      $('#sidebar').classList.remove('open');
      $('#mobile-scrim').classList.remove('visible');
    })
  );

  // Upload dismiss
  $('#upload-dismiss').addEventListener('click', () => { els.uploadProgress.hidden = true; });

  // Trash actions
  $('#restore-all').addEventListener('click', async () => {
    try {
      await request('/bin/restore-all', { method: 'POST' });
      toast('Files restored.', 'success');
      await loadView();
    } catch (error) { toast(error.message, 'error'); }
  });
  $('#empty-bin').addEventListener('click', async () => {
    if (!confirm('Permanently delete everything in the trash? This cannot be undone.')) return;
    try {
      await request('/bin/empty-all', { method: 'DELETE' });
      toast('Trash emptied.', 'success');
      await loadView();
    } catch (error) { toast(error.message, 'error'); }
  });

  // Profile / Sign out
  $('#profile-button').addEventListener('click', () => {
    if (confirm('Sign out of Dropzone?')) {
      localStorage.removeItem('dropzone.token');
      state.token = null;
      showAuth('login');
    }
  });

  // Modal backdrop click to close
  els.modal.addEventListener('click', event => {
    if (event.target === els.modal) els.modal.close();
  });

  // Workspace switch
  $('#workspace-switch').addEventListener('click', () => toast('You\'re in your personal space.'));

  // ─── Boot ───
  boot();
})();
