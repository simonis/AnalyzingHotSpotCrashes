#for f in "*.svg"; do sed -i '0,/<!-- Line -->/s//<!-- Text -->\n<text xml:space="preserve" x="6850" y="2976" fill="#000000"  font-family="Helvetica" font-style="normal" font-weight="bold" font-size="252" text-anchor="start">CodeCache<\/text>\n<!-- Line -->/' $f; done
for f in "*.svg"; do sed -i 's/font-size="252"/font-size="335"/g' $f; done
for f in "*.svg"; do sed -i 's/font-size="227"/font-size="280"/g' $f; done

for f in "*.svg"; do sed -i 's/font-family="Helvetica"/font-family="Sans"/g' $f; done
for f in "*.svg"; do sed -i 's/font-family="Courier"/font-family="Monospace"/g' $f; done
for f in "*.svg"; do sed -i 's/font-size="335"/font-size="300"/g' $f; done
for f in "*.svg"; do sed -i 's/>0</>69</g' $f; done
for f in "*.svg"; do sed -i 's/x="3070"/x="2929"/g' $f; done
