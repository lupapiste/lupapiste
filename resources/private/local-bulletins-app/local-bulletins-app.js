;(function() {
  "use strict";
  window.lupapisteApp = new LUPAPISTE.App({usagePurpose: util.usagePurposeFromUrl(),
                                           startPage: "local-bulletins",
                                           allowAnonymous: true,
                                           showUserMenu: false});

  $(function() {
      lupapisteApp.domReady();
      lupapisteApp.setTitle("Julkipano");

      var components = [
          {name: "local-bulletins"},
          {name: "application-bulletin"}];

      ko.registerLupapisteComponents(components);

      function BulletinModel(vetumaService) {
        var self = this;
        self.vetumaService = vetumaService;

        self.page = ko.observable().extend({
          limited: {values: ["local-bulletins", "r-bulletin"], defaultValue: "local-bulletins"}
        });

        self.bulletinId = ko.observable("");
        self.pageParams = {authenticated: self.vetumaService.authenticated};


        hub.onPageLoad("local-bulletins", function(e) {
          self.page(e.pageId);
          window.lupapisteApp.setTitle("Julkipano");
          pageutil.hideChatbot();
        });

        hub.onPageLoad("r-bulletin", function(e) {
          self.bulletinId(_.head(e.pagePath));
          self.page(e.pageId);
          window.lupapisteApp.setTitle("Julkipano");
          pageutil.hideChatbot();
        });

        self.page(pageutil.getPage());
      }

      var sharedModel = new BulletinModel(new LUPAPISTE.VetumaService());

      $("#local-bulletins").applyBindings(sharedModel);
      $("#r-bulletin").applyBindings(sharedModel);

      var errorType = _.includes(["error", "cancel"], pageutil.lastSubPage()) ?
        pageutil.lastSubPage() :
        undefined;
      hub.send("vetumaService::authenticateUser", {errorType: errorType});

    });
})();
