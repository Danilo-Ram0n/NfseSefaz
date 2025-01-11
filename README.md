```markdown
# Projeto de Assinatura A1 e Envio de XML para a SEFIN

---

## Descrição do Projeto
Este projeto foi desenvolvido como um protótipo para assinatura de NFSE's para ser acoplado num sistema, cujo tem as funcionalidades:
1. Carrega um certificado digital no formato `.pfx`.
2. Assina um arquivo XML, garantindo sua integridade e autenticidade.
3. Comprime o XML assinado em formato GZIP.
4. Converte o XML comprimido em Base64.
5. Envia o arquivo para a SEFIN utilizando uma requisição HTTP segura.

---

## Dependências e Pré-requisitos
- **Java 17 ou superior**
- **Bibliotecas necessárias:**
  - `javax.xml.crypto.dsig` para assinatura digital.
  - `javax.net.ssl` para configuração de SSL.
  - `java.util.zip` para compressão GZIP.
  - `java.net.http.HttpClient` para requisições HTTP.
- **Certificado digital no formato `.pfx`.**
- **XML base para assinatura no formato compatível com a SEFIN.**

---

## Arquitetura do Projeto
1. **Certificado Digital:** Carregado no código via `KeyStore`.
2. **Assinatura Digital:**
   - Usa a API `XMLSignatureFactory` para criar a assinatura.
   - Garante a inclusão do certificado no XML com o elemento `<Signature>`.
3. **Compressão GZIP:** Realizada antes da conversão para Base64.
4. **Envio à SEFIN:** Feito com o `HttpClient` configurado com SSL.

---

## Etapas do Processo

### 1. Configuração do Certificado Digital
- Carrega o certificado no formato `.pfx` utilizando `KeyStore`.
- Extrai a chave privada e o alias associados ao certificado.

### 2. Assinatura Digital
- Lê o XML base a ser assinado.
- Localiza o elemento `infDPS` no XML e identifica seu atributo `Id`.
- Gera a assinatura digital com:
  - Algoritmo de digestão: SHA1.
  - Algoritmo de assinatura: RSA-SHA1.
- Insere a assinatura no XML com o elemento `<Signature>`.

### 3. Compressão e Codificação
- Comprime o XML assinado em formato GZIP.
- Converte o resultado para Base64.

### 4. Envio para a SEFIN
- Configura um cliente HTTP com suporte a SSL e carrega o certificado digital.
- Envia o XML convertido no corpo de uma requisição POST para a API da SEFIN.

```
---

## Execução do Projeto
1. **Configuração Inicial:**
    - Insira o certificado digital no local configurado (`CERT_PATH`).
    - Insira a senha do certificado (`CERT_PASSWORD`).
    - Atualize o caminho do XML (`XML_FILE_PATH`).


2. **Saída Esperada:**
    - Status code da requisição HTTP.
    - Corpo da resposta da API da SEFIN.

```