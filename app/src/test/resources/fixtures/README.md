# Test fixtures

Real answers recorded from the live services on 2026-11-10 (personal data such as the client IP
in the Spotlight tracking URLs replaced with `0.0.0.0`):

| File | Request |
|------|---------|
| `bing_en-US.json`, `bing_de-DE.json` | `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=…` |
| `spotlight_v4.json` | `https://fd.api.iris.microsoft.com/v4/api/selection?placement=88000820&bcnt=4&country=US&locale=en-US&fmt=json` |
| `spotlight_v3.json` | `https://arc.msn.com/v3/Delivery/Placement?pid=338387&fmt=json&…` (fallback) |
| `wikimedia_expand.json`, `wikimedia_imageinfo.json` | Commons API, `expandtemplates` + `query&prop=imageinfo` for 2026-11-03…10 |
| `apod_wp_oct16-19.json`, `apod_wp_oct12.json` | `https://science.nasa.gov/wp-json/wp/v2/image-article?search=APOD…` (16 Oct = video, 18 Oct = YouTube) |

Unsplash and Pexels need a personal API key, so their fixtures are the example answers from the
official API documentation (`unsplash_random_docs.json`, `pexels_search_docs.json`).
