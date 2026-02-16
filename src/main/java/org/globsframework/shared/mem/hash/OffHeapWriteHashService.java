package org.globsframework.shared.mem.hash;

import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.util.Collection;


/*
Header : 'HHG' (HashKHeapGlob), version (1), hash size, hash version ?, [Index]*   // ==> on veux pouvoir changer l'index vers une nouvelle version de maniere atomic.
Index : indexData;
IndexData : hash, next index, data index, isValid // only update data index

Data File : 'HVHG', version, [DataIndex]*
DataIndex : isValid, nextIndex,
Data : [Values]*

-----------------------------------
Hash file : Header, version, hash size, [hash, next index, data index, isValid]* // => on cree le fichier plus grands pour les nouvelles valeurs
// => Si pas de donnée donc emplacement avec isValid a false et next index < 0 on écrit data index et isValid = true
// => Si changement de donnée et isValid = true on écrit juste le nouveau "data index"
// => Si même emplacement mais hash/key differents => on ecrit dans un nouvelle emplacement puis on ecrit le next.

Data file : Header, version, [isFree, [Values]*]*

On cree un fichier plus grands pour reserver de la place au nouvelles valeurs. IsFree a 1 indique un emplacement libre.

Comment passez de maniere atomique tous les index a 1

//scan pour chercher les emplacement free.


 */
public interface OffHeapWriteHashService {

    void save(Collection<Glob> data) throws IOException;

}
