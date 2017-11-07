;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({startPage: "local-bulletins",
                                           allowAnonymous: true,
                                           showUserMenu: false,
                                           componentPages: ["bulletin"]});

  $(function() {
      lupapisteApp.domReady();
      lupapisteApp.setTitle("Julkipano");

      var components = [
          {name: "local-bulletins-wrapper"},
          {name: "local-bulletins"},
          {name: "application-bulletin"}];

      ko.registerLupapisteComponents(components);

      $("#local-bulletins").applyBindings({vetumaService: new LUPAPISTE.VetumaService()});
    });
})();
