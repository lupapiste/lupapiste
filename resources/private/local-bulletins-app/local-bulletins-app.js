;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "local-bulletins",
                                           allowAnonymous: true,
                                           showUserMenu: false});
  $(function() {
      lupapisteApp.domReady();
      lupapisteApp.setTitle("Julkipano");

      var components = [
          {name: "local-bulletins"}];

      ko.registerLupapisteComponents(components);

      $("#local-bulletins").applyBindings({vetumaService: new LUPAPISTE.VetumaService()});
    });
})();
