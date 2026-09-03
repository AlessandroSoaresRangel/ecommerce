import { useState } from "react";

interface ProductImageProps {
  imageUrl: string | null;
  alt: string;
  aspectRatio?: string;
  fontSize?: number;
}

/** Mostra a imagem real do produto quando há imageUrl (e ele carrega); cai no
 *  placeholder "blueprint" quando não há imagem ou o carregamento falha. */
export function ProductImage({ imageUrl, alt, aspectRatio = "4/5", fontSize = 11 }: ProductImageProps) {
  const [failed, setFailed] = useState(false);

  if (imageUrl && !failed) {
    return (
      <img
        src={imageUrl}
        alt={alt}
        style={{ aspectRatio, width: "100%", objectFit: "cover", display: "block" }}
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <div className="duotone flex items-center justify-center" style={{ aspectRatio }}>
      <span
        className="px-2 text-center font-mono uppercase text-[#5d5d60]"
        style={{ letterSpacing: "0.12em", fontSize }}
      >
        sem imagem
      </span>
    </div>
  );
}
