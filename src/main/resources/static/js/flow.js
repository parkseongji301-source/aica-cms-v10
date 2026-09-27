(() => {
  const updateImageOptions = media => {
    if (!media.mime.startsWith('image/')) return;
    const selectors = '[data-image-select],select[data-field="imageId"]';
    const targets = [...document.querySelectorAll(selectors), ...document.querySelector('#section-template')?.content.querySelectorAll(selectors) || []];
    targets.forEach(select => {
      if ([...select.options].some(o => o.value === String(media.id))) return;
      const option = document.createElement('option'); option.value = media.id; option.textContent = media.name; select.append(option);
    });
  };
  document.addEventListener('media-uploaded', event => updateImageOptions(event.detail));
  document.addEventListener('change', async event => {
    const input = event.target;
    if (input.matches('[data-image-select]')) {
      const img = input.closest('[data-image-picker]').querySelector('[data-picked-image]');
      img.hidden = !input.value; if (input.value) img.src = '/admin/media/' + Number(input.value) + '/file';
    }
    if (!input.matches('[data-inline-image]') || !input.files.length) return;
    const picker = input.closest('[data-image-picker], [data-block-field="imageId"]');
    const select = picker.querySelector('select'), status = picker.querySelector('[data-upload-message]'), form = input.closest('form');
    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) { status.textContent = '5MB 이하의 이미지를 선택하세요.'; input.value = ''; return; }
    form.dataset.uploadPending = String(Number(form.dataset.uploadPending || 0) + 1);
    input.disabled = true; status.textContent = '업로드 중…';
    try {
      const data = new FormData(); data.append('file', file); data.append('imageOnly', 'true');
      const response = await fetch('/admin/media/upload', {method:'POST', body:data, headers:{'X-CSRF-TOKEN':document.querySelector('meta[name="csrf-token"]').content}});
      if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('업로드할 수 없습니다. 로그인 상태를 확인하세요.');
      const media = await response.json(); if (!response.ok) throw new Error(media.error || '업로드하지 못했습니다.');
      document.dispatchEvent(new CustomEvent('media-uploaded', {detail:media}));
      select.value = media.id; select.dispatchEvent(new Event('change', {bubbles:true})); status.textContent = '업로드 완료';
    } catch (error) {status.textContent = error.message;}
    finally {
      const pending = Number(form.dataset.uploadPending) - 1;
      if (pending) form.dataset.uploadPending = String(pending); else delete form.dataset.uploadPending;
      input.disabled = false; input.value = ''; form.dispatchEvent(new Event('input', {bubbles:true}));
    }
  });
})();
