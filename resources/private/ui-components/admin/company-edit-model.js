LUPAPISTE.CompanyEditModel = function(params) {
  "use strict";

  var self = this;

  self.company = ko.mapping.fromJS(_.defaults(params.company, {customAccountLimit: undefined, submitRestrictor: false}));


  self.accountTypes = LUPAPISTE.config.accountTypes.slice(); // copy
  self.accountTypes.push({name: "custom"});
  self.billingTypes = _.reduce(self.accountTypes, function(acc, type) {
    return _.union(acc, _.keys(type.price));
  }, []);
  self.showCustomCount = ko.pureComputed(function() {
    return self.company.accountType() === "custom";
  });
  self.showCustomCount.subscribe(function() { _.delay(hub.send, 50, "resize-dialog");});

  self.saveCompany = function() {

    var updates = _.pick(ko.mapping.toJS(self.company), ["accountType", "customAccountLimit", "billingType",
                                                         "submitRestrictor", "name", "y", "address1", "zip", "po"]);
    updates.customAccountLimit = parseInt(updates.customAccountLimit, 10);
    ajax
      .command("company-update",
               {company: self.company.id(),
                updates: updates})
      .success(function() {
        hub.send("company-updated");
        hub.send("close-dialog");})
      .call();
  };
};
