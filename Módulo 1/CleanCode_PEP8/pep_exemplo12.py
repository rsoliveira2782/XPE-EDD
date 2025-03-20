## Comentários que explicam o porquê, não o como

# Comentário Ruim
def is_product_greater_than_ten(num1, num2):
    ''' Vou multiplicar um número pelo outro '''
    product = num1 * num2

    ''' Vou comparar se o produto é maior que 10 '''
    return product > 10


# Comentário Bom
def is_product_greater_than_ten(num1, num2):
    ''' Produto dos números para avaliação do tamanho 10 '''
    product = num1 * num2
    return product > 10
