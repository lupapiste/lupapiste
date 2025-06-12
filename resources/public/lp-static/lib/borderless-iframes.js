var iframes = window.document.getElementsByTagName("iframe");
if (iframes) {
  for (var i = 0; i < iframes.length; i++) {
    // Set the non-standard frameBorder attribute for IE8
    iframes[i].frameBorder="0";
  }
}
