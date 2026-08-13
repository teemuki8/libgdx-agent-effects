# Texture State Restoration Design

**Status:** Approved bug-fix scope for GitHub issue #2.

## Problem

Binding more than one sampler changes `GL_ACTIVE_TEXTURE` and leaves the last sampler unit active
after preview rendering. That mutable GL selector belongs to the host application.

## Approaches considered

1. **Snapshot and restore the selector (selected).** Read `GL_ACTIVE_TEXTURE` before uniform
   binding and restore that exact enum in cleanup. This is narrow and preserves arbitrary host
   state.
2. Always reset to `GL_TEXTURE0`. This fixes common SpriteBatch use but overwrites a legitimate
   nonzero host selection.
3. Bind every sampler on unit zero. This cannot support shaders that sample multiple inputs.

## Design

`PreviewRenderer.render` snapshots only `GL_ACTIVE_TEXTURE` before sampler binding. Its existing
cleanup restores that value before returning or propagating a render failure. No other GL state is
captured or reset. Texture upload selects its assigned unit before construction so a later upload
cannot unbind an earlier sampler. A real two-sampler Xvfb test proves both pixel-correct unit
assignment and restoration of the previous selector.
