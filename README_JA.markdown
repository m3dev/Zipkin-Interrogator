# Zipkin Interrogator

Zipkinのデータからボトルネックをよりわかりやすくするためのアプリ。

## 使い方

1. ホムに行って、サービスの名前をクリックする。

    ![home](docs/images/home.png)

2. Spanの名前をクリックする。

    ![spans](docs/images/spans.png)

3. Trace id をクリックする。

    ![traces](docs/images/traces.png)

4. 分解をクリックする。

    ![analysis](docs/images/analysis.png)

## Legend

- 赤いボックスが付いているSpanは同時に行ってるSpanのなかで、一番遅い。
- 黄色のボックスが付いてるSpanは同時に行ってるSpanのなかで、一番遅くないが、1秒以上かかったから注意するべき。