;(function() {
  "use strict";

  function OrganizationModel() {
    var self = this;
    var isLoading = false;
    self.organization = ko.observable();
    self.permanentArchiveEnabled = ko.observable(false);
    self.indicator = ko.observable(false).extend({notify: "always"});
    self.pending = ko.observable();

    self.permitTypes = ko.observableArray([]);
    self.municipalities = ko.observableArray([]);

    self.open = function(orgId) {

      ajax
        .query("organization-by-id", {organizationId: orgId})
        .pending(self.pending)
        .success(function(result) {
          self.organization(ko.mapping.fromJS(result.data));
          isLoading = true;
          self.permanentArchiveEnabled(result.data["permanent-archive-enabled"]);
          isLoading = false;
        })
        .call();

      return false;
    };

    self.convertOpenInforequests = function() {
      ajax.command("convert-to-normal-inforequests", {organizationId: self.organization().id()})
        .success(function(resp) {
          var msg = loc("admin.converted.inforequests", resp.n);
          LUPAPISTE.ModalDialog.showDynamicOk(loc("infoRequests"), msg);})
        .call();
    };

    self.openInfoRequests = ko.pureComputed(function() {
      return self.organization() && self.organization().scope && _.reduce(self.organization().scope(), function(result, s) {return result || s["open-inforequest"]();}, false);
    });

    self.saveRow = function(s) {
      var scope = ko.mapping.toJS(s);

      var openingMills = null;
      if (scope.opening) {
        openingMills = new Date(scope.opening).getTime();
      }

      var data = {permitType: scope.permitType,
                  municipality: scope.municipality,
                  inforequestEnabled: scope["inforequest-enabled"],
                  applicationEnabled: scope["new-application-enabled"],
                  openInforequestEnabled: scope["open-inforequest"],
                  openInforequestEmail: scope["open-inforequest-email"],
                  opening: openingMills};

      ajax.command("update-organization", data)
        .success(function() {LUPAPISTE.ModalDialog.showDynamicOk(util.getIn(self.organization(), ["name", loc.getCurrentLanguage()]), loc("saved"));})
        .call();
      return false;
    };

    self.newScope = function(model) {
      if (!self.pending()) {
        hub.send("show-dialog", {title: "Lis&auml;&auml; lupatyyppi",
                                 size: "medium",
                                 component: "create-scope",
                                 componentParams: {organization: model.organization(),
                                                   permitTypes:  self.permitTypes(),
                                                   municipalities: self.municipalities()}});
      }
    };

    self.permanentArchiveEnabled.subscribe(function(value) {
      if (isLoading) {
        return;
      }
      ajax.command("set-organization-permanent-archive-enabled", {organizationId: self.organization().id(), enabled: value})
        .success(function() {
          self.indicator(true);
          self.organization()["permanent-archive-enabled"](value);
        })
        .call();
    });

    ajax
      .query("permit-types")
      .success(function(d) {
        self.permitTypes(d.permitTypes);
      })
      .call();

    ajax
      .query("municipalities")
      .success(function(d) {
        self.municipalities(d.municipalities);
      })
      .call();
  }

  var organizationModel = new OrganizationModel();

  hub.onPageLoad("organization", function(pageLoad) {
    var orgId = _.last(pageLoad.pagePath);
    organizationModel.open(orgId);
  });

  hub.subscribe("organization::scope-added", function(data) {
    organizationModel.open(data.orgId);
  });


  $(function() {
    $("#organization").applyBindings({organizationModel:organizationModel});

    ko.components.register("create-scope", {viewModel: LUPAPISTE.CreateScopeModel, template: {element: "create-scope-template"}});
  });

})();
