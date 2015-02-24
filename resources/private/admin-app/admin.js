;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App("admin", false, true);

  $(function() {
    window.lupapisteApp.domReady();
    LUPAPISTE.ModalDialog.init();
    $("#admin").applyBindings({});
  });

})();
