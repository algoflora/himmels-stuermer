lambda_name = "{{lambda-name}}"
lambda_memory_size = "{{lambda-memory-size}}"
{% if lambda-timeout %}
lambda_timeout = {{lambda-timeout}}
{% endif %}
lambda_architectures = ["{{lambda-architecture}}"]

image_name = "{{image-name}}"
image_tag = "{{image-tag}}"

bot_token = "{{bot-token}}"
