from PyQt5.QtWidgets import QApplication
from PyQt5.QtGui import QPainter, QColor, QFont, QPixmap  # Fixed import
from PyQt5.QtCore import Qt
import sys

def create_coin_icon():
    app = QApplication(sys.argv)
    
    # Create a pixmap
    pixmap = QPixmap(64, 64)
    pixmap.fill(Qt.transparent)
    
    # Draw a coin
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)
    painter.setBrush(QColor(241, 196, 15))  # Gold color
    painter.setPen(Qt.NoPen)
    painter.drawEllipse(8, 8, 48, 48)
    
    # Add a C symbol
    painter.setFont(QFont("Arial", 24, QFont.Bold))
    painter.setPen(QColor(44, 62, 80))  # Dark blue
    painter.drawText(pixmap.rect(), Qt.AlignCenter, "C")
    painter.end()
    
    # Save the icon
    pixmap.save("coin.ico", "ICO")
    
    print("Coin icon created as coin.ico")

if __name__ == "__main__":
    create_coin_icon()