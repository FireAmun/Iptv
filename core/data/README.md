# Sources and local data

JVM module: `:core:data`. The current slice provides the bounded streaming M3U parser and cancellable OkHttp loader with safe failure codes. Room/catalogue persistence and the remaining capabilities below are not implemented yet.

Own M3U/Xtream-compatible adapters, bounded streaming imports, repositories, Room/Paging, search, EPG, refresh coordination, and protected source persistence. Depend on shared models and preserve user state independently of replaceable catalogue generations.
