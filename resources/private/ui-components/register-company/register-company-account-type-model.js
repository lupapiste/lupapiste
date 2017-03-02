LUPAPISTE.RegisterCompanyAccountTypeModel = function() {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.companyRegistrationService;

  self.accountTypes = service.accountTypes;

  self.selected = service.registration.accountType;
  self.cancelClick = _.noop;
  self.continueClick = _.noop;
  self.continueEnable = self.selected;

  self.selectedCss = function( id ) {
    var isSelected = id === self.selected();
    return {selected: isSelected,
            "other-selected": self.selected() && !isSelected,
            "not-selected": !isSelected};
  };
};
