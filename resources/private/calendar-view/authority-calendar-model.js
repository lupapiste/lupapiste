LUPAPISTE.ApplicationAuthorityCalendarModel = function (params) {

  self.authorizedParties = lupapisteApp.models.application.roles;

  self.partyFullName = function(party) {
    return party.firstName() + " " + party.lastName();
  };

};
