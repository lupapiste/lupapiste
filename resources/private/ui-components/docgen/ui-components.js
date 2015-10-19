var uiComponents = (function() {
  "use strict";

  var sizeClasses = { "t": "tiny", "s": "short", "m": "medium", "l": "long"};

  // TODO: show warning indicator
  // TODO: refactor
  var saveMany = function(command, documentId, applicationId, element, paths, vals, indicator, result, cb) {
    cb = cb ? cb : function() {};
    var updates = _(paths)
      .map(function(p) { return p.join("."); })
      .zip(vals)
      .value();
    ajax
      .command(command, {
        doc: documentId,
        id: applicationId,
        updates: updates,
        collection: "documents"})
      .success(function (e) {
        var res = _.find(e.results, function(result) {
          return _.isEqual(result.path, paths);
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

  var save = function(command, documentId, applicationId, element, path, val, indicator, result, cb) {
    return saveMany(command, documentId, applicationId, element, [path], [val], indicator, result, cb)
  }

  return {
    sizeClasses: sizeClasses,
    save: save,
    saveMany: saveMany,
  };

})();
