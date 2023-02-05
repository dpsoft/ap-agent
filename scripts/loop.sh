#!/bin/bash
event=${1:-itimer}
profiling_duration=${2:-30}
results_folder=${3:-profiling_results}
output=${4:-flame}

mkdir -p $results_folder

while true; do
    timestamp=$(date +%Y-%m-%d_%H-%M-%S)
    if [[ $output == "fp" ]]
    then
        output_file="${event}_profile_$timestamp.json.gz"
    else
        output_file="${event}_profile_$timestamp.html"
    fi
    start_time=$(date +%s)
    curl -s "http://localhost:8080/profiler/profile?event=$event&output=$output&duration=$profiling_duration" -o "$results_folder/$output_file"
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    echo "Profile saved to $results_folder/$output_file at $(date) took $duration seconds."
done