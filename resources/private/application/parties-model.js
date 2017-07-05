LUPAPISTE.PartiesModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  self.personSelectorItems = ko.observableArray();

  self.refresh = function(application) {
    var authArray = application.auth;
    var companies = _.filter(authArray, ["type", "company"]);

    var validPersons = _.filter(authArray, function(user) {
      return user.firstName && user.lastName &&
             !_.includes( ["statementGiver", "guest", "guestAuthority"], user.role);
    });

    if (!_.isEmpty(companies)) {
      ajax.query("company-users-for-person-selector", {id: application.id})
        .success(function(result) {
          var companyUsers = result.users;
          self.personSelectorItems(_.unionBy(validPersons, companyUsers, "id"));
        })
        .call();
    } else {
      self.personSelectorItems(validPersons);
    }
  };

};