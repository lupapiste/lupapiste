;(function() {
  "use strict";

  // Search term matches, when every subterm is included in
  // organization id or any of the organization names. Matching is
  // case-insensitive.  For example, term "sip rak val" matches
  // "Sipoon rakennusvalvonta".
  function isSearchMatch( organization, term ) {
    var targets = [];
    var terms = _.split( term, /\s+/ );
    targets.push( organization.id );
    targets = _.map( _.concat( targets, _.values(organization.name)),
                     _.toLower);
    return _.every( terms, function( term) {
      return _.some( targets, function( t ) {
        return _.includes( t, term );
      });
    });
  }

  function NewOrganizationModel(options) {
    var self = this;
    self.orgId = ko.observable("");
    self.municipality = ko.observable("");
    self.name = ko.observable("");
    var permitTypes = ko.observableArray();
    self.permitTypes = ko.pureComputed({
      read: function() { return _.join(permitTypes(), " "); },
      write: function(s) { permitTypes(_.split(_.toUpper(s), " ")); }
    });

    self.isValid = function() {
      return !_.isEmpty(_.toUpper(self.orgId())) &&
        !_.isEmpty(self.municipality()) &&
        !_.isEmpty(self.name()) &&
        !_.isEmpty(permitTypes());
    };

    self.save = function() {
      ajax
        .command("create-organization", {
          "org-id": self.orgId(),
          "municipality": self.municipality(),
          "name": self.name(),
          "permit-types": permitTypes()
        })
        .success( options.onSave || _.noop )
        .call();
    };
  }

  function OrganizationsModel() {
    var self = this;

    self.searchTerm = ko.observable();
    var filterFn = function( term, org ) {
      var termLower = _.toLower(term);
      return _.trim( termLower ) ? isSearchMatch( org, termLower) : false;
    };
    self.organizations = ko.observableArray([]);
    self.selectedOrganizations = ko.pureComputed(function() {
      var searchInput = _.trim(self.searchTerm());
      if (_.isEmpty(searchInput)) {
        // empty string, 'showAll'
        return _.sortBy(self.organizations(), function(o) {
          return o.name[loc.getCurrentLanguage()];
        });
      } else {
        return _(self.organizations())
                .filter(_.partial(filterFn, searchInput))
                .sortBy(function(o) { return o.name[loc.getCurrentLanguage()]; })
                .value();
      }
    });
    self.pending = ko.observable();
    self.newOrganization = ko.observable();
    self.searchFocus = ko.observable(false);


    self.showAll = function() {
      self.searchTerm("");
    };

    self.load = function() {
      self.searchFocus(true);
      ajax
        .query("organizations")
        .pending(self.pending)
        .success(function(d) {
          self.organizations(d.organizations);
        })
        .call();
    };
    self.countText = ko.pureComputed(function() {
      var orgs = self.selectedOrganizations();
      var count = _.size( orgs );
      var searchInput = self.searchTerm();
      if (_.isUndefined(searchInput)) {
        // don't show count, when nothing has been typed (initial view)
        return "";
      } else {
        return _.trim( self.searchTerm()) || _.some(orgs)
          ? sprintf( "%d organisaatio%s.",
                     count,
                     count !== 1 ? "ta" : "")
        : "";
      }
    });

    function redirectAfterCreate() {
      var id = util.getIn(self.newOrganization, ["orgId"]);
      self.newOrganization(null);
      self.load();
      if (id) {
        pageutil.openPage("organization", id);
      }
    }

    self.createOrganization = function() {
      self.newOrganization(new NewOrganizationModel({
        onSave: redirectAfterCreate
      }));
    };

    self.cancelCreate = function() {
      self.newOrganization(null);
    };
  }

  var organizationsModel = new OrganizationsModel();

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
          var redirectLocation = self.role() === "authorityAdmin" ? self.role() : "authority";
          window.location.href = "/app/fi/" + _.kebabCase(redirectLocation);
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
  });

})();
