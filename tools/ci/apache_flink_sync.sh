#!/usr/bin/env bash

set -e

DRY_RUN=${1:-true}
REMOTE="af"

echo DRYRUN: $DRY_RUN

git remote add $REMOTE git@github.com:apache/flink.git
git fetch $REMOTE

# Gets release and master branch references from apache
RELEASE_BRANCHES=`git branch -a | grep "$REMOTE/release-" | grep -v "rc" | sort -V -r | head -n 5`
TARGETS=$RELEASE_BRANCHES`git branch -a | grep "$REMOTE/master" | head -n 1`

# Pushes apache release-x.y/master to confluentinc apache-release-x.y/apache-master
for TARGET in $TARGETS ; do
  SYNC_BRANCH=`echo $TARGET | rev | cut -d/ -f1 | rev`
  REF="refs/$TARGET:refs/heads/apache-$SYNC_BRANCH"
  echo "Pushing to CF: $REF"
  if [ $DRY_RUN = "false" ]
  then
    git push origin "$REF"
  else
    echo "Skipping push $TARGET - apache-$SYNC_BRANCH due to dryrun."
  fi
done
