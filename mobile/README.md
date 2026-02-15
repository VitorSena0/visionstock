# VisionStock Mobile

Projeto Expo ativo:

`mobile/vision-stock-mobile`

Use os comandos a partir dessa pasta:

```bash
cd mobile/vision-stock-mobile
npm install
npm run start
```

## API URL para login

Crie `mobile/vision-stock-mobile/.env` com:

```bash
EXPO_PUBLIC_API_URL=http://SEU_IP_DA_MAQUINA:8080
```

Exemplos:

- Celular fisico na mesma rede: `http://192.168.x.x:8080`
- Android Emulator: `http://10.0.2.2:8080`

## Problemas comuns

- Login falha: confira se o backend Spring esta rodando e acessivel no IP acima.
- Tela sem estilo: rode com cache limpo para recarregar NativeWind.

```bash
cd mobile/vision-stock-mobile
npm run start -- --clear
```
