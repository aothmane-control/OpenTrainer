#!/bin/bash

# Create simple placeholder icons using ImageMagick (if available) or skip
if command -v convert &> /dev/null; then
    echo "Creating launcher icons with ImageMagick..."
    
    # Create icons with different sizes
    convert -size 48x48 xc:#005CBC -fill white -gravity center -pointsize 24 -annotate +0+0 "K" app/src/main/res/mipmap-mdpi/ic_launcher.png
    convert -size 48x48 xc:#005CBC -fill white -gravity center -pointsize 24 -annotate +0+0 "K" app/src/main/res/mipmap-mdpi/ic_launcher_round.png
    
    convert -size 72x72 xc:#005CBC -fill white -gravity center -pointsize 36 -annotate +0+0 "K" app/src/main/res/mipmap-hdpi/ic_launcher.png
    convert -size 72x72 xc:#005CBC -fill white -gravity center -pointsize 36 -annotate +0+0 "K" app/src/main/res/mipmap-hdpi/ic_launcher_round.png
    
    convert -size 96x96 xc:#005CBC -fill white -gravity center -pointsize 48 -annotate +0+0 "K" app/src/main/res/mipmap-xhdpi/ic_launcher.png
    convert -size 96x96 xc:#005CBC -fill white -gravity center -pointsize 48 -annotate +0+0 "K" app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
    
    convert -size 144x144 xc:#005CBC -fill white -gravity center -pointsize 72 -annotate +0+0 "K" app/src/main/res/mipmap-xxhdpi/ic_launcher.png
    convert -size 144x144 xc:#005CBC -fill white -gravity center -pointsize 72 -annotate +0+0 "K" app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
    
    convert -size 192x192 xc:#005CBC -fill white -gravity center -pointsize 96 -annotate +0+0 "K" app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
    convert -size 192x192 xc:#005CBC -fill white -gravity center -pointsize 96 -annotate +0+0 "K" app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
    
    echo "Icons created successfully!"
else
    echo "ImageMagick not found. You'll need to create launcher icons manually or use Android Studio's Image Asset tool."
    echo "The app will use default Android icons for now."
fi
