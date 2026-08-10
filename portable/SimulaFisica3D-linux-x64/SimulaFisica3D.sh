#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -x "$APP_DIR/runtime/bin/java" ]]; then
  JAVA_BIN="$APP_DIR/runtime/bin/java"
else
  JAVA_BIN="$(command -v java || true)"
fi

if [[ -z "${JAVA_BIN:-}" ]]; then
  if command -v zenity >/dev/null 2>&1; then
    zenity --error --title="SimulaFisica 3D" --text="Java nao foi encontrado. Instale Java 21 ou coloque um JDK Linux portatil na pasta runtime."
  else
    printf '%s\n' "Java nao foi encontrado. Instale Java 21 ou coloque um JDK Linux portatil na pasta runtime."
    read -r -p "Pressione Enter para sair..."
  fi
  exit 1
fi

# O JavaFX (com bibliotecas nativas) e resolvido como modulos nomeados via --module-path.
# O app (nao-modular) vai apenas no classpath; excluimos os jars do JavaFX do -cp para
# nao expor os mesmos jars em module-path e classpath ao mesmo tempo (evita erros de
# split-package/resolucao de modulos em algumas JVMs).
APP_CP=""
for jar in "$APP_DIR"/lib/*.jar; do
  case "$(basename "$jar")" in
    javafx-*) ;;
    *) APP_CP="${APP_CP:+$APP_CP:}$jar" ;;
  esac
done

# "$@" repassa os argumentos recebidos pelo lancador (ex.: --module=solar_system)
# para a aplicacao. Sem isso, abrir um modulo especifico pela linha de comando
# seria impossivel na versao Linux, ao contrario da versao Windows.
exec "$JAVA_BIN" \
  --module-path "$APP_DIR/lib" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp "$APP_CP" \
  engine.Main "$@"
