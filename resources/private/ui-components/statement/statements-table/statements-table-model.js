LUPAPISTE.StatementsTableModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.statements = params.statements;
  self.authorization = params.authModel;
  self.localisationKeys = params.localisationKeys;


  self.statementIdsWithAttachments = ko.pureComputed(function() {
    var statementIdsWithAttachments = [];
    _.forEach(lupapisteApp.services.attachmentsService.attachments(),
              function(attachment) {
                var targetType = util.getIn(attachment, ["target", "type"]);
                if (targetType === "statement") {
                  statementIdsWithAttachments.push(util.getIn(attachment, ["target", "id"]));
                }
              });
    return _.uniq(statementIdsWithAttachments);
  });

  self.hasAttachment = function(statement) {
    return _.includes(self.statementIdsWithAttachments(), statement.id());
  };

  self.isGiven = function(statement) {
    return _.includes(["given", "replyable", "replied"], util.getIn(statement, ["state"]));
  };

  var isAuthorityOrStatementOwner = function(statement) {
    var currentUser = lupapisteApp.models.currentUser;
    return _.includes(util.getIn(currentUser, ["orgAuthz", self.application.organization()]), "authority")
      || util.getIn(statement, ["person", "userId"]) === currentUser.id();
  };

  self.isRemovable = function(statement) {
    return isAuthorityOrStatementOwner(statement) && !self.isGiven(statement);
  };

  self.canAccessStatement = function(statement) {
    return self.isGiven(statement) || isAuthorityOrStatementOwner(statement);
  };

  self.isStatementOverDue = function(statement) {
    var nowTimeInMs = new Date().getTime();
    return (statement.dueDate && self.isGiven(statement) ) ? (nowTimeInMs > statement.dueDate()) : false;
  };

  self.repliesEnabled = ko.pureComputed(function() {
    return self.authorization.ok("statement-replies-enabled");
  });

  self.isReplyable = function(statement) {
    return _.includes(["replyable"], util.getIn(statement, ["state"]));
  };

  self.showReplyState = function(statement) {
    var user = lupapisteApp.models.currentUser;
    if (self.application.userHasRole(user, "owner")) {
      return _.includes(["replied"], util.getIn(statement, ["state"]));
    } else {
      return _.includes(["replyable", "replied"], util.getIn(statement, ["state"]));
    }
  };

  var deleteStatementFromServer = function(statementId) {
    ajax
      .command("delete-statement", {id: self.application.id(), statementId: statementId})
      .success(function() {
        repository.load(self.application.id());
        return false;
      })
      .call();
    return false;
  };

  self.openStatement = function(tab, model) {
    pageutil.openPage("statement", [self.application.id(), model.id(), tab].join("/"));
    return false;
  };

  self.openDeleteDialog = function(model) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("statement.delete.header"),
        loc("statement.delete.message"),
        {title: loc("yes"), fn: _.partial(deleteStatementFromServer, model.id())},
        {title: loc("no")}
    );
  };

};
