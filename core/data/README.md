# Sources and local data

JVM module: `:core:data`. Provides bounded streaming M3U and Xtream Player API adapters, protected source encoding and the persistence contract, with cancellable OkHttp calls and safe failure codes. Xtream authenticates, imports live/movie/series catalogues and fetches episodes. Gson is used only as a streaming reader, without reflective binding. Android Room/Keystore implementation is isolated in `:core:storage`; search/EPG and other catalogue capabilities remain open.

Own M3U/Xtream-compatible adapters, bounded streaming imports, repositories, Room/Paging, search, EPG, refresh coordination, and protected source persistence. Depend on shared models and preserve user state independently of replaceable catalogue generations.
