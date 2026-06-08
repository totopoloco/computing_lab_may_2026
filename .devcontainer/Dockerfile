# Development container for Spring Boot with Java 25
FROM eclipse-temurin:25-jdk-jammy

# Install essential tools + comprehensive network/internet toolset
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
  # --- Core utilities ---
  bash \
  git \
  curl \
  wget \
  unzip \
  zip \
  vim \
  sudo \
  procps \
  lsof \
  htop \
  nodejs \
  npm \
  dos2unix \
  # --- Core networking (interfaces, routing, sockets) ---
  iputils-ping \
  iputils-tracepath \
  iproute2 \
  net-tools \
  traceroute \
  mtr-tiny \
  # --- DNS & WHOIS ---
  dnsutils \
  whois \
  # --- Port & service scanning ---
  nmap \
  netcat-openbsd \
  hping3 \
  # --- TLS / certificates ---
  openssl \
  ca-certificates \
  # --- Packet capture & analysis ---
  tcpdump \
  tshark \
  # --- SSH & file transfer ---
  openssh-client \
  rsync \
  ftp \
  sshpass \
  # --- HTTP clients & load testing ---
  httpie \
  apache2-utils \
  siege \
  wrk \
  # --- Tunnelling / proxying ---
  socat \
  stunnel4 \
  proxychains4 \
  # --- Bandwidth testing ---
  iperf3 \
  # --- Traffic monitoring ---
  iftop \
  nethogs \
  nload \
  bmon \
  # --- ARP / link-layer tools ---
  arping \
  arp-scan \
  # --- Other protocols ---
  telnet \
  snmp \
  ldap-utils \
  # --- JSON & web text processing ---
  jq \
  lynx \
  w3m \
  # --- Document compilation (Markdown → PDF via pandoc + LaTeX) ---
  pandoc \
  texlive-latex-base \
  texlive-latex-recommended \
  texlive-latex-extra \
  texlive-fonts-recommended \
  texlive-xetex \
  && rm -rf /var/lib/apt/lists/*

# Install grpcurl (gRPC command-line client) — arch-aware
RUN GRPCURL_VERSION=1.9.1 && \
    ARCH=$(dpkg --print-architecture) && \
    GRPCURL_ARCH=$([ "$ARCH" = "amd64" ] && echo "x86_64" || echo "arm64") && \
    curl -sSL "https://github.com/fullstorydev/grpcurl/releases/download/v${GRPCURL_VERSION}/grpcurl_${GRPCURL_VERSION}_linux_${GRPCURL_ARCH}.tar.gz" \
    | tar -xz -C /usr/local/bin grpcurl

RUN npm install -g n && n 20

# Create a non-root user with sudo access
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME \
  && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
  && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
  && chmod 0440 /etc/sudoers.d/$USERNAME \
  # Create .gradle directory with correct ownership for the vscode user
  && mkdir -p /home/$USERNAME/.gradle \
  && chown -R $USERNAME:$USERNAME /home/$USERNAME/.gradle

# Set working directory
WORKDIR /workspace

# Copy entrypoint script and ensure Unix line endings (fixes Windows CRLF issue)
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/entrypoint.sh && chmod a+x /usr/local/bin/entrypoint.sh

# Switch to non-root user
USER $USERNAME

# Install claude-code into a user-writable npm prefix so auto-updates work without sudo
RUN npm config set prefix ~/.npm-global && \
    npm install -g @anthropic-ai/claude-code

# Set JAVA_HOME and expose the user-local npm bin directory
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/home/vscode/.npm-global/bin:${JAVA_HOME}/bin:${PATH}"

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["sleep", "infinity"]
