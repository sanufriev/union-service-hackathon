#!/bin/sh

buildAndExportTag() {
  TAG="hackathon-$RELEASE-build.$1"
  export TAG
}

RELEASE=1.64
APPLICATION_ENVIRONMENT=testnet
CMD=$1

DEPLOY_COMPOSE_FILE=docker-compose-generated.yaml

BUILD_FILE=.build
INFRA_ENV_FILE=.env

export APPLICATION_ENVIRONMENT

test -f "$INFRA_ENV_FILE" && export $(cat "$INFRA_ENV_FILE")

envsubst < "docker-compose.template" > $DEPLOY_COMPOSE_FILE

test -f "$BUILD_FILE" && export $(cat "$BUILD_FILE")

if [ "${BUILD_NUMBER}" = "" ]; then
  BUILD_NUMBER=0
fi

if [ "${CMD}" = "build" ]; then
  BUILD_NUMBER=$(( $BUILD_NUMBER + 1))
  buildAndExportTag $BUILD_NUMBER
  docker compose -f $DEPLOY_COMPOSE_FILE build

  echo "BUILD_NUMBER=$BUILD_NUMBER" > "$BUILD_FILE"
elif [ "${CMD}" = "deploy" ]; then
  buildAndExportTag $BUILD_NUMBER
  docker stack deploy -c $DEPLOY_COMPOSE_FILE hackathon
fi
