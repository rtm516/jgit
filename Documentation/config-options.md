# JGit configuration options

## Legend

| git option | description |
|------------|-------------|
| &#x2705; | option defined by native git |
| &#x20DE; | jgit custom option not supported by native git |

## __core__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `core.attributesFile` | | &#x2705; | In addition to `.gitattributes` (per-directory) and `.git/info/attributes`, Git looks into this file for attributes . Path expansions are made the same way as for `core.excludesFile`. |
| `core.autocrlf` | `false` | &#x2705; | Setting this variable to `true` is the same as setting the text attribute to `auto` on all files and `core.eol` to `crlf`. Set to `true` if you want to have CRLF line endings in your working directory and the repository has LF line endings. This variable can be set to `input`, in which case no output conversion is performed. |
| `core.bare` | set automatically on init or clone | &#x2705; | If true this repository is assumed to be bare and has no working directory associated with it. If this is the case a number of commands that require a working directory will be disabled |
| `core.bigFileThreshold` | `50 MiB` | &#x2705; | Files larger than this size are stored deflated, without attempting delta compression. Storing large files without delta compression avoids excessive memory usage, at the slight expense of increased disk usage. Additionally files larger than this size are always treated as binary. |
| `core.checkstat` |  | &#x2705; | When missing or is set to `default`, many fields in the stat structure are checked to detect if a file has been modified since Git looked at it. Checks as much of the dircache stat info as possible (in JGit limited by Java filesystem API). When set to `minimum` only checks the size and whole second part of time stamp when comparing the stat info in the dircache with actual file stat info. |
| `core.compression` | `-1` (zlib default) | &#x2705; | An integer `-1..9`, indicating a default compression level. `-1` is the zlib default. `0` means no compression, and `1..9` are various speed/size tradeoffs, `9` being slowest.|
| `core.deltaBaseCacheLimit` | `10 MiB` | &#x2705; | Maximum number of bytes to reserve for caching base objects that multiple deltafied objects reference. By storing the entire decompressed base object in a cache Git is able to avoid unpacking and decompressing frequently used base objects multiple times. |
| `core.dfs.blockLimit` | `30 MiB` | &#x20DE; | Maximum number bytes of heap memory to dedicate to caching pack file data in DFS block cache. |
| `core.dfs.blockSize` | `64 kiB` | &#x20DE; | Size in bytes of a single window read in from the pack file into the DFS block cache. |
| `core.dfs.concurrencyLevel` | `32` | &#x20DE; | The estimated number of threads concurrently accessing the DFS block cache. |
| `core.dfs.deltaBaseCacheLimit` | `10 MiB` | &#x20DE; | Maximum number of bytes to hold in per-reader DFS delta base cache. |
| `core.dfs.streamFileThreshold` | `50 MiB` | &#x20DE; | The size threshold beyond which objects must be streamed. |
| `core.dfs.streamBuffer` | Block size of the pack | &#x20DE; | Number of bytes to use for buffering when streaming a pack file during copying. If 0 the block size of the pack is used|
| `core.dfs.streamRatio` | `0.30` | &#x20DE; | Ratio of DFS block cache to occupy with a copied pack. Values between `0` and `1.0`. |
| `core.dirNoGitLinks` | `false` | &#x20DE; | If set to `true` avoid checking for submodules. See [bug 436200](https://bugs.eclipse.org/bugs/show_bug.cgi?id=436200). |
| `core.eol` | `native` | &#x2705; | Sets the line ending type to use in the working directory for files that are marked as text (either by having the text attribute set, or by having `text=auto` and Git auto-detecting the contents as text). Alternatives are `lf`, `crlf` and `native`, which uses the platform’s native line ending. |
| `core.excludesFile` | | &#x2705; | Specifies the pathname to the file that contains patterns to describe paths that are not meant to be tracked, in addition to `.gitignore` (per-directory) and `.git/info/exclude`. |
| `core.fileMode` | Auto detects if file modes are supported | &#x2705; | Tells Git if the executable bit of files in the working tree is to be honored. |
| `core.hideDotFiles` | `dotGitOnly` | &#x2705; | Windows only. If `true`, mark newly-created directories and files whose name starts with a dot as hidden. If `dotGitOnly`, only the `.git/` directory is hidden, but no other files starting with a dot. |
| `core.hooksPath` | `$GIT_DIR/hooks` | &#x2705; | Path to look for hooks. |
| `core.logAllRefUpdates` | `true` in a repository with working tree, `false` in bare repository | &#x2705; | Enable the reflog. |
| `core.packedGitLimit` | `10 MiB` | &#x2705; | Maximum number of bytes to cache in memory from pack files. |
| `core.packedGitMmap` | `false` | &#x2705; | Whether to use Java NIO virtual memory mapping for JGit buffer cache. When set to `true` enables use of Java NIO virtual memory mapping for cache windows, `false` reads entire window into a `byte[]` with standard read calls. `true` is experimental and may cause instabilities and crashes since Java doesn't support explicit unmapping of file regions mapped to virtual memory. |
| `core.packedGitOpenFiles` | `128` | &#x20DE; | Maximum number of streams to open at a time. Open packs count against the process limits. |
| `core.packedGitUseStrongRefs` | `false` | &#x20DE; | Whether the window cache should use strong references (`true`) or SoftReferences (`false`). When `false` the JVM will drop data cached in the JGit block cache when heap usage comes close to the maximum heap size. |
| `core.packedGitWindowSize` | `8 kiB` | &#x2705; | Number of bytes of a pack file to load into memory in a single read operation. This is the "page size" of the JGit buffer cache, used for all pack access operations. All disk IO occurs as single window reads. Setting this too large may cause the process to load more data than is required; setting this too small may increase the frequency of read() system calls. |
| `core.precomposeUnicode` | `true` on Mac OS | &#x2705; | MacOS only. When `true`, JGit reverts the unicode decomposition of filenames done by Mac OS. |
| `core.quotePath` | `true` | &#x2705; | Commands that output paths (e.g. ls-files, diff), will quote "unusual" characters in the pathname by enclosing the pathname in double-quotes and escaping those characters with backslashes in the same way C escapes control characters (e.g. `\t` for TAB, `\n` for LF, `\\` for backslash) or bytes with values larger than `0x80` (e.g. octal `\302\265` for "micro" in UTF-8). |
| `core.repositoryFormatVersion` | `1` | &#x20DE; | Internal version identifying the repository format and layout version. Don't set manually. |
| `core.streamFileThreshold` | `50 MiB` | &#x20DE; | The size threshold beyond which objects must be streamed. |
| `core.supportsAtomicFileCreation` | `true` | &#x20DE; | Whether the filesystem supports atomic file creation. |
| `core.symlinks` | Auto detect if filesystem supports symlinks| &#x2705; | If false, symbolic links are checked out as small plain files that contain the link text. |
| `core.trustFolderStat` | `true` | &#x20DE; | Whether to trust the pack folder's modification time. If `false` JGit will always scan the `.git/objects/pack` folder to check for new pack files. This can help to workaround caching issues on NFS, but reduces performance. If set to `true` it uses the `lastmodified` attribute of the folder and assumes that no new pack files can be in this folder if its modification time has not changed. |
| `core.worktree` | Root directory of the working tree if it is not the parent directory of the `.git` directory | &#x2705; | The path to the root of the working tree. |

## __gc__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `gc.aggressiveDepth` | `50` | &#x2705; | The depth parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.aggressiveWindow` | `250` | &#x2705; | The window size parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.auto` | `6700` | &#x2705; | Number of loose objects until auto gc combines all loose objects into a pack and consolidates all existing packs into one. Setting to 0 disables automatic packing of loose objects. |
| `gc.autoDetach` | `true` |  &#x2705; | Make auto gc return immediately and run in background. |
| `gc.autoPackLimit` | `50` |  &#x2705; | Number of packs until auto gc consolidates existing packs (except those marked with a .keep file) into a single pack. Setting `gc.autoPackLimit` to 0 disables automatic consolidation of packs. |
| `gc.logExpiry` | `1.day.ago` | &#x2705; | If the file `gc.log` exists, then auto gc will print its content and exit successfully instead of running unless that file is more than `gc.logExpiry` old. |
| `gc.pruneExpire` | `2.weeks.ago` | &#x2705; | Grace period after which unreachable objects will be pruned. |
| `gc.prunePackExpire` | `1.hour.ago` |  &#x20DE; | Grace period after which packfiles only containing unreachable objects will be pruned. |

## __pack__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `pack.bitmapContiguousCommitCount` | `100` | &#x20DE; | Count of most recent commits for which to build bitmaps. |
| `pack.bitmapDistantCommitSpan` | `5000` | &#x20DE; | Span of commits when building bitmaps for distant history. |
| `pack.bitmapExcessiveBranchCount` | `100` | &#x20DE; | The count of branches deemed "excessive". If the count of branches in a repository exceeds this number and bitmaps are enabled, "inactive" branches will have fewer bitmaps than "active" branches. |
| `pack.bitmapInactiveBranchAgeInDays` | `90` | &#x20DE; | Age in days that marks a branch as "inactive" for bitmap creation. |
| `pack.bitmapRecentCommitCount` | `20000`  | &#x20DE; | Count at which to switch from `bitmapRecentCommitSpan` to `bitmapDistantCommitSpan`. |
| `pack.bitmapRecentCommitSpan` | `100` | &#x20DE; | Span of commits when building bitmaps for recent history. |
| `pack.buildBitmaps` | `true` | &#x20DE; synonym for `repack.writeBitmaps` | Whether index writer is allowed to build bitmaps for indexes. |
| `pack.compression` | `core.compression` | &#x2705; | Compression level applied to objects in the pack. |
| `pack.cutDeltaChains` | `false` | &#x20DE; | Whether existing delta chains should be cut at {@link #getMaxDeltaDepth() |
| `pack.deltaCacheLimit` | `100` | &#x2705; | Maximum size in bytes of a delta to cache. |
| `pack.deltaCacheSize` | `50 MiB` | &#x2705; | Size of the in-memory delta cache. |
| `pack.deltaCompression` | `true` | &#x20DE; | Whether the writer will create new deltas on the fly. `true` if the pack writer will create a new delta when either `pack.reuseDeltas` is false, or no suitable delta is available for reuse. |
| `pack.depth` | `50` | &#x2705; | Maximum depth of delta chain set up for the pack writer. |
| `pack.indexVersion` | `2` | &#x2705; | Pack index file format version. |
| `pack.minSizePreventRacyPack` | `100 MiB` | &#x20DE; | Minimum packfile size for which we wait before opening a newly written pack to prevent its lastModified timestamp could be racy if `pack.waitPreventRacyPack` is `true`. |
| `pack.preserveOldPacks` | `false` | &#x20DE; | Whether to preserve old packs in a preserved directory. |
| `prunePreserved`, only via API of PackConfig | `false` | &#x20DE; | Whether to remove preserved pack files in a preserved directory. |
| `pack.reuseDeltas` | `true` |&#x20DE; | Whether to reuse deltas existing in repository. |
| `pack.reuseObjects` | `true` | &#x20DE; | Whether to reuse existing objects representation in repository. |
| `pack.singlePack` | `false` | &#x20DE; | Whether all of `refs/*` should be packed in a single pack. |
| `pack.threads` | `0` (auto-detect number of processors) | &#x2705; | Number of threads to use for delta compression. |
| `pack.waitPreventRacyPack` | `false` | &#x20DE; | Whether we wait before opening a newly written pack to prevent its lastModified timestamp could be racy. |
| `pack.window` | `10` | &#x2705; | Number of objects to try when looking for a delta base per thread searching for deltas. |
| `pack.windowMemory` | `0` (unlimited) | &#x2705; | Maximum number of bytes to put into the delta search window. |
