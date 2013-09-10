LUPAPISTE.VerdictsModel = function() {
  var self = this;

  function getApplicationId(bindings) {
    return bindings.application.id();
  }

  self.verdicts = ko.observable();
  self.attachments = ko.observable();
  self.response = ko.observable();

  self.refresh = function(application) {
    self.verdicts(application.verdicts);
    self.attachments(_.filter(application.attachments,function(attachment) {
      return _.isEqual(attachment.target, {type: "verdict"});
    }));
  };

  self.openVerdict = function(bindings) {
    window.location.hash = "#!/verdict/" + getApplicationId(bindings);
    return false;
  };

  self.checkVerdict = function(bindings){
    ajax.command("check-for-verdict", {id: getApplicationId(bindings)})
    .success(function(resp) {
      self.response(JSON.stringify(resp.response));
    })
    .call();
  };
};
