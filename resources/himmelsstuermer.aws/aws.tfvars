lambda_name = "{{lambda-name}}"
lambda_memory_size = "{{lambda-memory-size}}"
{% if lambda-timeout %}
lambda_timeout = {{lambda-timeout}}
{% endif %}
lambda_architectures = ["{{lambda-architecture}}"]

lambda_jar_file = "{{lambda-jar-file}}"

# image_name = "{{image-name}}"
# image_tag = "{{image-tag}}"

bot_token = "{{bot-token}}"
