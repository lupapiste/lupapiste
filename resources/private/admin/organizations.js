;(function() {
  "use strict";

  var isLoading = false;

  function OrganizationModel() {
    var self = this;
    self.organization = ko.observable({});
    self.permanentArchiveEnabled = ko.observable(false);
    self.indicator = ko.observable(false).extend({notify: "always"});

    self.open = function(organization) {
      // date picker needs an obervable
      self.organization(ko.mapping.fromJS(organization));
      isLoading = true;
      self.permanentArchiveEnabled(organization["permanent-archive-enabled"]);
      isLoading = false;
      pageutil.openPage("organization");
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
      return self.organization().scope && _.reduce(self.organization().scope(), function(result, s) {return result || s["open-inforequest"]();}, false);
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

  }

  var organizationModel = new OrganizationModel();

  function OrganizationsModel(orgModel) {
    var self = this;

    self.organizations = ko.observableArray([]);
    self.pending = ko.observable();

    self.load = function() {
      ajax
        .query("organizations")
        .pending(self.pending)
        .success(function(d) {
          var organizations = _.map(d.organizations, function(o) {o.open = _.partial(orgModel.open, o); return o;});
          self.organizations(_.sortBy(organizations, function(o) { return o.name[loc.getCurrentLanguage()]; }));
        })
        .call();
    };
  }

  var organizationsModel = new OrganizationsModel(organizationModel);

  function LoginAsModel() {
    var self = this;
    self.role = ko.observable("authority");
    self.password = ko.observable("");
    self.organizationId = null;

    self.open = function(organization) {
      self.organizationId = organization.id;
      self.password("");
      LUPAPISTE.ModalDialog.open("#dialog-login-as");
    };

    self.login = function() {
      ajax
        .command("impersonate-authority", {organizationId: self.organizationId, role: self.role(), password: self.password()})
        .success(function() {
          window.location.href = "/app/fi/" + _.kebabCase(self.role());
        })
        .call();
      return false;
    };
  }
  var loginAsModel = new LoginAsModel();

  hub.onPageLoad("organizations", organizationsModel.load);

  $(function() {
    $("#organizations").applyBindings({
      "organizationsModel": organizationsModel,
      "loginAsModel": loginAsModel
    });
    $("#organization").applyBindings({organizationModel:organizationModel});
  });

})();
