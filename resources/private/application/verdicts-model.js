LUPAPISTE.VerdictsModel = function() {
  var self = this;

  self.verdicts = ko.observable();
  self.attachments = ko.observable();
  self.response = ko.observable();

  self.refresh = function(application) {
    self.verdicts(application.verdicts);
    self.attachments(_.filter(application.attachments,function(attachment) {
      return _.isEqual(attachment.target, {type: "verdict"});
    }));
  };

  self.openVerdict = function() {
    window.location.hash = "#!/verdict/" + currentId; // FIXME
    return false;
  };
  self.checkVerdict = function(){
    ajax.command("check-for-verdict", {id: application.id()}) // FIXME
    .success(function(resp) {
      self.response(JSON.stringify(resp.response));
    })
    .call();
  };
};
