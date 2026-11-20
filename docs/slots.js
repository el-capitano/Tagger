window.hallmarkSlots = (images) => {
  for (const [name, value] of Object.entries(images)) {
    const slot = document.querySelector(`[data-slot="${CSS.escape(name)}"]`);
    if (!slot) continue;
    const image = new Image();
    image.src = typeof value === 'string' ? value : value.src;
    image.alt = (typeof value === 'object' && value.alt) || slot.dataset.alt || '';
    slot.querySelectorAll(':scope > img').forEach((old) => old.remove());
    slot.prepend(image);
  }
};
