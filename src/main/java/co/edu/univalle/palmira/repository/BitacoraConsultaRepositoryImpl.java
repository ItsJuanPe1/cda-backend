package co.edu.univalle.palmira.repository;

import co.edu.univalle.palmira.model.entity.Bitacora;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Se arma la consulta con Criteria para no enviar parámetros nulos a PostgreSQL. */
class BitacoraConsultaRepositoryImpl implements BitacoraConsultaRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Bitacora> buscar(String usuario, String entidad, Long periodoId,
                                 Instant desde, Instant hasta, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Bitacora> cq = cb.createQuery(Bitacora.class);
        Root<Bitacora> root = cq.from(Bitacora.class);
        cq.where(filtros(cb, root, usuario, entidad, periodoId, desde, hasta));
        cq.orderBy(cb.desc(root.get("fecha")), cb.desc(root.get("id")));

        List<Bitacora> contenido = em.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> conteo = cb.createQuery(Long.class);
        Root<Bitacora> rootConteo = conteo.from(Bitacora.class);
        conteo.select(cb.count(rootConteo))
                .where(filtros(cb, rootConteo, usuario, entidad, periodoId, desde, hasta));
        long total = em.createQuery(conteo).getSingleResult();

        return new PageImpl<>(contenido, pageable, total);
    }

    private Predicate[] filtros(CriteriaBuilder cb, Root<Bitacora> root, String usuario, String entidad,
                                Long periodoId, Instant desde, Instant hasta) {
        List<Predicate> p = new ArrayList<>();
        if (usuario != null) {
            String patron = "%" + usuario + "%";
            p.add(cb.or(
                    cb.like(cb.lower(root.get("usuarioCorreo")), patron),
                    cb.like(cb.lower(root.get("operadorNombre")), patron)));
        }
        if (entidad != null) {
            p.add(cb.equal(cb.upper(root.get("entidad")), entidad));
        }
        if (periodoId != null) {
            p.add(cb.equal(root.get("periodoId"), periodoId));
        }
        if (desde != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (hasta != null) {
            p.add(cb.lessThan(root.get("fecha"), hasta));
        }
        return p.toArray(new Predicate[0]);
    }
}
