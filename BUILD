load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "gcs-java-bench",
    srcs = glob(["src/main/java/**/*.java"]),
    main_class = "com.google.cloud.benchmark.Main",
    deps = [
        "@maven//:com_google_cloud_google_cloud_storage",
        "@maven//:info_picocli_picocli",
        "@maven//:org_apache_commons_commons_math3",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_cloud_google_cloud_core",
        "@maven//:com_google_cloud_google_cloud_core_grpc",
        "@maven//:io_grpc_grpc_api",
        "@maven//:com_google_api_grpc_grpc_google_cloud_storage_v2",
        "@maven//:com_google_api_grpc_proto_google_cloud_storage_v2",
        "@maven//:io_grpc_grpc_netty_shaded",
        "@maven//:io_grpc_grpc_auth",
        "@maven//:io_grpc_grpc_stub",
        "@maven//:io_grpc_grpc_protobuf",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_auth_google_auth_library_oauth2_http",
    ],
)
