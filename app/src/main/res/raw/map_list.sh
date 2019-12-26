#!/usr/bin/env bash


COLOR_LOG="\e[36m"
COLOR_ERR="\e[31m"
COLOR_RESET="\e[0m"

links_file="map_links.txt"
map_list_page_path="map_list.html"


if [[ $1 = "add" ]]; then
    if [[  $# -ne 2 ]]; then
        echo -e "${COLOR_ERR}Error add command have one parameter (link)${COLOR_RESET}" 1>&2
        exit
    fi
    echo ADDDDDD
elif [[ $1 = "rebuild" ]]; then
    if [[  $# -ne 1 ]]; then
        echo -e "${COLOR_ERR}Error rebuild command doesn't have more parameters${COLOR_RESET}" 1>&2
        exit
    fi

    while read link; do
        map_id=$(echo $link | cut -d "=" -f 2 | cut -d "&" -f 1)
        map_title=$(wget -O - "$link" 2>/dev/null | tr "[" "\n" | grep '\\"mf.map\\",\\"'"$map_id"'\\",\\"' | cut -d '"' -f 6 | rev | cut -c 1 --complement | rev)

    done < "$links_file"







else
    echo -e "${COLOR_ERR}Error wrong command: ${1}${COLOR_RESET}" 1>&2
fi



