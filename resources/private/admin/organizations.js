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

  function OrganizationsModel() {
    var self = this;

    var all = [];
    self.organizations = ko.observableArray([]);
    self.pending = ko.observable();
    self.searchTerm = ko.observable();
    self.search = function() {
      self.organizations( _.filter( all,
                                    function( org ) {
                                      var term = _.toLower(self.searchTerm());
                                      return _.trim( term ) && isSearchMatch( org, term);
                                    }));
    };

    self.load = function() {
      ajax
        .query("organizations")
        .pending(self.pending)
        .success(function(d) {
          all = _.sortBy(d.organizations, function(o) { return o.name[loc.getCurrentLanguage()]; });
          // Refresh the old results. For example, when returning from
          // organization view.
          if( self.searchTerm()) {
            self.search();
          } else {
            if( _.size( self.organizations())) {
              self.showAll();
            }
          }
        })
        .call();
    };
    self.countText = function() {
      var count = _.size( self.organizations());
      return _.trim( self.searchTerm()) || _.some( self.organizations())
        ? _.sprintf( "%d organisaatio%s.",
                     count,
                     count !== 1 ? "ta" : "")
      : "";
    };
    self.showAll = function() {
      self.searchTerm("");
      self.organizations( all );
    };
  }

  var organizationsModel = new OrganizationsModel();

  function LoginAsModel() {
    var self = this;
    self.role = ko.observable("approver");
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
