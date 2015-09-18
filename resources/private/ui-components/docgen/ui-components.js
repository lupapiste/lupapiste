var uiComponents = (function() {
  "use strict";

  var sizeClasses = { "t": "tiny", "s": "short", "m": "medium", "l": "long"};

  // TODO: show warning indicator
  // TODO: refactor
  var save = function(command, documentId, applicationId, element, path, val, indicator, result, cb) {
    cb = cb ? cb : function() {};
    var updates = [[path.join("."), val]];
    ajax
      .command(command, {
        doc: documentId,
        id: applicationId,
        updates: updates,
        collection: "documents"})
      .success(function (e) {
        var res = _.find(e.results, function(result) {
          return _.isEqual(result.path, path);
        });
        result(res ? res.result : undefined);
        indicator({type: "saved"});
        cb();
      })
      .error(function () {
        indicator({type: "err"});
      })
      .fail(function () {
        indicator({type: "err"});
      })
      .call();
  };

  return {
    sizeClasses: sizeClasses,
    save: save
  };

})();
