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
captured or reset. A real two-sampler Xvfb test proves the previous selector is restored while
existing pixel-correctness tests continue to prove sampler binding itself.
