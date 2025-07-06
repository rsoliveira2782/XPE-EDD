import subprocess
import sys

# Instala as dependências
subprocess.check_call([sys.executable, "-m", "pip", "install", "--break-system-packages", "-r", "./simulations/requirements.txt"])


# Executa o programa principal (substitua 'main.py' pelo seu script)
# subprocess.run([sys.executable, "main.py"])
