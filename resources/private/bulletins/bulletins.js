;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "bulletins",
                                           allowAnonymous: true,
                                           showUserMenu: false,
                                           componentPages: ["bulletin"]});

  window.lupapisteApp.services.documentDataService = new LUPAPISTE.DocumentDataService({
    readOnly: true
  });

  $(function() {
    lupapisteApp.domReady();
    lupapisteApp.setTitle("Julkipano");

    var components = [
        {name: "bulletins"},
        {name: "application-bulletins"},
        {name: "application-bulletins-list"},
        {name: "load-more-application-bulletins"},
        {name: "application-bulletin"},
        {name: "bulletins-search"},
        {name: "autocomplete-municipalities"},
        {name: "autocomplete-states"},
        {name: "bulletin-comment"},
        {name: "bulletin-comment-box"},
        {name: "bulletin-attachments-tab"},
        {name: "bulletin-verdicts-tab"},
        {name: "begin-vetuma-auth-button"},
        {name: "bulletin-info-tab"},
        {name: "bulletin-instructions-tab"}];

    _.forEach(components, function(component) {
      ko.components.register(component.name, {
        viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
        template: { element: (component.template ? component.template : component.name + "-template")}
      });
    });

    var dummyAuth = { ok: function() { return false; }};
    $("#bulletins").applyBindings({ bulletinService: new LUPAPISTE.ApplicationBulletinsService(),
                                    vetumaService: new LUPAPISTE.VetumaService(),
                                    fileuploadService: new LUPAPISTE.FileuploadService(),
                                    auth: dummyAuth });

    var errorType = _.includes(["error", "cancel"], pageutil.lastSubPage()) ?
      pageutil.lastSubPage() :
      undefined;
    hub.send("vetumaService::authenticateUser", {errorType: errorType});
  });
})();
