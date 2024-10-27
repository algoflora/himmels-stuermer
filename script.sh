#!/bin/bash

# Find all namespaces declared in source files
for ns in $(grep -r -h "(ns " src | cut -d " " -f 2); do
  # Check if the namespace is used anywhere else in the project
  if ! grep -r "$ns" src/ | grep -v "(ns $ns" > /dev/null; then
    echo "Unused namespace: $ns"
  fi
done
