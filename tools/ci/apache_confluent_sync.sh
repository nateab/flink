#!/usr/bin/env bash

set -euo pipefail

DRY_RUN=${1:-true}

echo DRYRUN: $DRY_RUN

RELEASE_BRANCHES=$(git branch -a | grep 'origin/release.*-confluent$' | sort -V -r | head -n 5)
MASTER_BRANCH="remotes/origin/master"
TARGETS="$RELEASE_BRANCHES $MASTER_BRANCH"
echo $TARGETS

for TARGET in $TARGETS ; do
  CONFLUENT_SYNC_BRANCH=$(echo $TARGET | rev | cut -d/ -f1 | rev)
  APACHE_SYNC_BRANCH=apache-${CONFLUENT_SYNC_BRANCH%"-confluent"}
  echo "Syncing Branches: $APACHE_SYNC_BRANCH to $CONFLUENT_SYNC_BRANCH"
  git fetch origin $APACHE_SYNC_BRANCH
  git fetch origin $CONFLUENT_SYNC_BRANCH
  git checkout --progress --force -B $APACHE_SYNC_BRANCH "refs/remotes/origin/$APACHE_SYNC_BRANCH"
  git checkout --progress --force -B $CONFLUENT_SYNC_BRANCH "refs/remotes/origin/$CONFLUENT_SYNC_BRANCH"
  if [ $DRY_RUN = "false" ]
  then
    gh pr create -B $CONFLUENT_SYNC_BRANCH -H $APACHE_SYNC_BRANCH -t "Merge branch '$APACHE_SYNC_BRANCH' into $CONFLUENT_SYNC_BRANCH" -b ""
    gh pr merge $APACHE_SYNC_BRANCH --auto --merge
  else
    echo "Skipping creating pr branch $CONFLUENT_SYNC_BRANCH with head $APACHE_SYNC_BRANCH due to dryrun."
  fi
done
